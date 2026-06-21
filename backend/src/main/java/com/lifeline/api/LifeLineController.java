package com.lifeline.api;

import com.lifeline.dispatch.DispatchDecision;
import com.lifeline.dispatch.DispatchEngine;
import com.lifeline.dispatch.NoDispatchCandidateException;
import com.lifeline.domain.Ambulance;
import com.lifeline.domain.AmbulanceLocationSnapshot;
import com.lifeline.domain.AmbulanceStatus;
import com.lifeline.domain.DispatchAuditRecord;
import com.lifeline.domain.Hospital;
import com.lifeline.domain.Incident;
import com.lifeline.domain.IncidentStatus;
import com.lifeline.domain.Location;
import com.lifeline.domain.Notification;
import com.lifeline.domain.NotificationRole;
import com.lifeline.domain.OutboxEvent;
import com.lifeline.domain.SecurityAuditEvent;
import com.lifeline.domain.Trip;
import com.lifeline.location.AmbulanceLocationProjection;
import com.lifeline.notifications.NotificationService;
import com.lifeline.outbox.OutboxProcessor;
import com.lifeline.outbox.OutboxPublishResult;
import com.lifeline.outbox.OutboxSummary;
import com.lifeline.outbox.OutboxSummaryService;
import com.lifeline.security.AuthenticatedUser;
import com.lifeline.security.CurrentUserService;
import com.lifeline.security.SecurityAuditService;
import com.lifeline.security.UserRole;
import com.lifeline.simulation.SimulationRequest;
import com.lifeline.simulation.SimulationResult;
import com.lifeline.simulation.SimulationService;
import com.lifeline.store.LifeLineStore;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api")
public class LifeLineController {
    private final LifeLineStore store;
    private final DispatchEngine dispatchEngine;
    private final OutboxProcessor outboxProcessor;
    private final OutboxSummaryService outboxSummaryService;
    private final AmbulanceLocationProjection ambulanceLocationProjection;
    private final NotificationService notificationService;
    private final SimulationService simulationService;
    private final CurrentUserService currentUserService;
    private final SecurityAuditService auditService;

    public LifeLineController(
            LifeLineStore store,
            DispatchEngine dispatchEngine,
            OutboxProcessor outboxProcessor,
            OutboxSummaryService outboxSummaryService,
            AmbulanceLocationProjection ambulanceLocationProjection,
            NotificationService notificationService,
            SimulationService simulationService,
            CurrentUserService currentUserService,
            SecurityAuditService auditService
    ) {
        this.store = store;
        this.dispatchEngine = dispatchEngine;
        this.outboxProcessor = outboxProcessor;
        this.outboxSummaryService = outboxSummaryService;
        this.ambulanceLocationProjection = ambulanceLocationProjection;
        this.notificationService = notificationService;
        this.simulationService = simulationService;
        this.currentUserService = currentUserService;
        this.auditService = auditService;
    }

    @GetMapping("/ambulances")
    public List<Ambulance> ambulances() {
        return visibleAmbulances(currentUser());
    }

    @GetMapping("/ambulance-locations")
    public List<AmbulanceLocationSnapshot> ambulanceLocations() {
        AuthenticatedUser user = currentUser();
        Set<String> visibleAmbulanceIds = visibleAmbulances(user).stream()
                .map(Ambulance::id)
                .collect(Collectors.toSet());
        return ambulanceLocationProjection.snapshots().stream()
                .filter(snapshot -> visibleAmbulanceIds.contains(snapshot.ambulanceId()))
                .toList();
    }

    @GetMapping("/hospitals")
    public List<Hospital> hospitals() {
        return visibleHospitals(currentUser());
    }

    @GetMapping("/incidents")
    public List<IncidentView> incidents() {
        AuthenticatedUser user = currentUser();
        return visibleIncidents(user).stream()
                .map(incident -> incidentView(incident, user))
                .toList();
    }

    @GetMapping("/trips")
    public List<Trip> trips() {
        return visibleTrips(currentUser());
    }

    @GetMapping("/dispatch-decisions")
    public List<DispatchAuditRecord> dispatchDecisions() {
        AuthenticatedUser user = currentUser();
        requireControl(user, "Only control can view dispatch decision history.");
        return store.dispatchDecisions();
    }

    @GetMapping("/outbox-events")
    public List<OutboxEvent> outboxEvents() {
        AuthenticatedUser user = currentUser();
        requireControl(user, "Only control can view outbox events.");
        return store.outboxEvents();
    }

    @GetMapping("/outbox-events/pending")
    public List<OutboxEvent> pendingOutboxEvents() {
        AuthenticatedUser user = currentUser();
        requireControl(user, "Only control can view pending outbox events.");
        return store.pendingOutboxEvents(50);
    }

    @GetMapping("/notifications")
    public List<Notification> notifications(@RequestParam String role) {
        AuthenticatedUser user = currentUser();
        NotificationRole requestedRole = parseRole(role);
        if (!user.isControl() && requestedRole != notificationRole(user)) {
            throw forbidden(user, "notification.read", "NotificationRole", requestedRole.name(), "Cannot read another role's notifications.");
        }
        return notificationService.notificationsFor(requestedRole);
    }

    @PostMapping("/notifications/{notificationId}/ack")
    public Notification acknowledgeNotification(@PathVariable String notificationId) {
        AuthenticatedUser user = currentUser();
        if (!user.isControl()) {
            boolean ownsNotification = notificationService.notificationsFor(notificationRole(user)).stream()
                    .anyMatch(notification -> notification.id().equals(notificationId));
            if (!ownsNotification) {
                auditService.denied(
                        user,
                        "notification.ack",
                        "Notification",
                        notificationId,
                        "Notification is outside the actor role scope.",
                        Map.of()
                );
                throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Notification not found.");
            }
        }
        Notification notification = notificationService.acknowledge(notificationId);
        auditService.allowed(
                user,
                "notification.ack",
                "Notification",
                notificationId,
                "Notification acknowledged.",
                Map.of("role", notification.role().name(), "eventType", notification.eventType())
        );
        return notification;
    }

    @GetMapping("/simulations")
    public List<SimulationResult> simulations() {
        AuthenticatedUser user = currentUser();
        requireControl(user, "Only control can view simulations.");
        return simulationService.simulations();
    }

    @GetMapping("/simulations/{simulationId}")
    public SimulationResult simulation(@PathVariable String simulationId) {
        AuthenticatedUser user = currentUser();
        requireControl(user, "Only control can view simulations.");
        return simulationService.simulation(simulationId);
    }

    @GetMapping("/audit-events")
    public List<SecurityAuditEvent> auditEvents(@RequestParam(defaultValue = "100") int limit) {
        AuthenticatedUser user = currentUser();
        requireControl(user, "Only control can view security audit events.");
        return auditService.events(limit);
    }

    @PostMapping("/simulations")
    @ResponseStatus(HttpStatus.CREATED)
    public SimulationResult runSimulation(@Valid @RequestBody SimulationRequest request) {
        AuthenticatedUser user = currentUser();
        requireControl(user, "Only control can run simulations.");
        SimulationResult result = simulationService.run(request);
        auditService.allowed(
                user,
                "simulation.run",
                "Simulation",
                result.id(),
                "Simulation run created.",
                Map.of("incidentCount", request.incidentCount(), "strategy", request.strategy().name())
        );
        return result;
    }

    @GetMapping("/outbox-events/summary")
    public OutboxSummary outboxSummary() {
        AuthenticatedUser user = currentUser();
        requireControl(user, "Only control can view outbox summary.");
        return outboxSummaryService.summarize(store.outboxEvents(), Instant.now());
    }

    @GetMapping("/metrics")
    public MetricsResponse metrics() {
        AuthenticatedUser user = currentUser();
        List<Incident> incidents = visibleIncidents(user);
        List<Ambulance> ambulances = visibleAmbulances(user);
        List<Hospital> hospitals = visibleHospitals(user);
        List<Trip> trips = visibleTrips(user);
        List<SimulationResult> simulations = user.isControl() ? store.simulations() : List.of();

        double averageBedAvailability = hospitals.stream()
                .mapToDouble(hospital -> hospital.totalBeds() == 0 ? 0 : (double) hospital.availableBeds() / hospital.totalBeds())
                .average()
                .orElse(0);
        List<OutboxEvent> outboxEvents = user.isControl() ? store.outboxEvents() : List.of();
        int failedOutboxEvents = (int) outboxEvents.stream()
                .filter(event -> event.publishedAt() == null && event.lastPublishError() != null)
                .count();
        int notificationBacklog = user.isControl()
                ? store.notificationBacklog()
                : (int) notificationService.notificationsFor(notificationRole(user)).stream().filter(Notification::unread).count();

        return new MetricsResponse(
                (int) incidents.stream().filter(incident -> incident.status() == IncidentStatus.NEW).count(),
                (int) ambulances.stream().filter(ambulance -> ambulance.status() == AmbulanceStatus.AVAILABLE).count(),
                trips.size(),
                (int) hospitals.stream().filter(Hospital::hasCapacity).count(),
                Math.round(averageBedAvailability * 1000.0) / 10.0,
                (int) outboxEvents.stream().filter(event -> event.publishedAt() == null).count(),
                (int) outboxEvents.stream().filter(event -> event.publishedAt() != null).count(),
                failedOutboxEvents,
                failedOutboxEvents,
                ambulanceLocations().size(),
                notificationBacklog,
                simulations.size(),
                latestOptimizationImprovement(simulations)
        );
    }

    @PostMapping("/outbox-events/publish")
    public OutboxPublishResult publishOutboxEvents() {
        AuthenticatedUser user = currentUser();
        requireControl(user, "Only control can publish outbox events.");
        OutboxPublishResult result = outboxProcessor.publishPendingNow();
        auditService.allowed(
                user,
                "outbox.publish",
                "Outbox",
                "pending",
                "Pending outbox events published.",
                Map.of("published", result.published(), "failed", result.failed(), "pending", result.pending())
        );
        return result;
    }

    @PostMapping("/incidents")
    @ResponseStatus(HttpStatus.CREATED)
    public IncidentView createIncident(@Valid @RequestBody CreateIncidentRequest request) {
        AuthenticatedUser user = currentUser();
        if (user.role() != UserRole.PATIENT && !user.isControl()) {
            throw forbidden(user, "incident.create", "Incident", "new", "Only patients and control can create incidents.");
        }
        Incident incident = store.createIncident(
                user.username(),
                request.patientName(),
                request.phone(),
                request.condition(),
                request.priority(),
                new Location(request.latitude(), request.longitude())
        );
        auditService.allowed(
                user,
                "incident.create",
                "Incident",
                incident.id(),
                "Incident created.",
                Map.of("condition", incident.condition().name(), "priority", incident.priority().name())
        );
        return incidentView(incident, user);
    }

    @PostMapping("/ambulances/{ambulanceId}/location")
    public AmbulanceLocationSnapshot updateAmbulanceLocation(
            @PathVariable String ambulanceId,
            @Valid @RequestBody UpdateAmbulanceLocationRequest request
    ) {
        AuthenticatedUser user = currentUser();
        if (!user.isControl() && (user.role() != UserRole.DRIVER || !ambulanceId.equals(user.ambulanceId()))) {
            throw forbidden(user, "ambulance.location.update", "Ambulance", ambulanceId, "Drivers can update only their assigned ambulance.");
        }
        store.findAmbulance(ambulanceId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Ambulance not found."));
        AmbulanceLocationSnapshot snapshot = ambulanceLocationProjection.update(
                ambulanceId,
                new Location(request.latitude(), request.longitude()),
                Instant.now()
        );
        auditService.allowed(
                user,
                "ambulance.location.update",
                "Ambulance",
                ambulanceId,
                "Ambulance location updated.",
                Map.of("latitude", request.latitude(), "longitude", request.longitude())
        );
        return snapshot;
    }

    @PostMapping("/dispatch")
    public DispatchResponse dispatch(@Valid @RequestBody DispatchRequest request) {
        AuthenticatedUser user = currentUser();
        requireControl(user, "Only control can dispatch incidents.");
        Incident incident = store.findIncident(request.incidentId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Incident not found."));

        DispatchDecision decision = dispatchEngine.decide(incident, ambulanceLocationProjection.applyTo(store.ambulances()), store.hospitals());
        Trip trip = store.commitAssignment(
                incident.id(),
                decision.ambulance().id(),
                decision.hospital().id(),
                decision.winningScore(),
                decision.alternatives()
        );

        Incident updatedIncident = store.findIncident(incident.id()).orElseThrow();
        Ambulance updatedAmbulance = ambulanceLocationProjection.applyTo(store.findAmbulance(decision.ambulance().id()).orElseThrow());
        Hospital updatedHospital = store.findHospital(decision.hospital().id()).orElseThrow();

        auditService.allowed(
                user,
                "dispatch.reserve",
                "Trip",
                trip.id(),
                "Incident dispatched.",
                Map.of("incidentId", incident.id(), "ambulanceId", trip.ambulanceId(), "hospitalId", trip.hospitalId())
        );

        return new DispatchResponse(
                incidentView(updatedIncident, user),
                updatedAmbulance,
                updatedHospital,
                trip,
                decision.winningScore(),
                decision.alternatives()
        );
    }

    @PostMapping("/trips/{tripId}/status")
    public Trip updateTripStatus(
            @PathVariable String tripId,
            @Valid @RequestBody UpdateTripStatusRequest request
    ) {
        AuthenticatedUser user = currentUser();
        Trip trip = store.findTrip(tripId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Trip not found."));
        if (!user.isControl() && (user.role() != UserRole.DRIVER || !trip.ambulanceId().equals(user.ambulanceId()))) {
            throw forbidden(user, "trip.status.update", "Trip", tripId, "Drivers can update only their assigned trips.");
        }
        Trip updatedTrip = store.updateTripStatus(tripId, request.status());
        auditService.allowed(
                user,
                "trip.status.update",
                "Trip",
                tripId,
                "Trip status updated.",
                Map.of("status", request.status().name())
        );
        return updatedTrip;
    }

    @PostMapping("/hospitals/{hospitalId}/capacity")
    public Hospital updateHospitalCapacity(
            @PathVariable String hospitalId,
            @Valid @RequestBody UpdateHospitalCapacityRequest request
    ) {
        AuthenticatedUser user = currentUser();
        if (!user.isControl() && (user.role() != UserRole.HOSPITAL || !hospitalId.equals(user.hospitalId()))) {
            throw forbidden(user, "hospital.capacity.update", "Hospital", hospitalId, "Hospitals can update only their assigned facility.");
        }
        Hospital hospital = store.updateHospitalCapacity(hospitalId, request.availableBeds());
        auditService.allowed(
                user,
                "hospital.capacity.update",
                "Hospital",
                hospitalId,
                "Hospital capacity updated.",
                Map.of("availableBeds", request.availableBeds())
        );
        return hospital;
    }

    @PostMapping("/trips/{tripId}/reroute")
    public DispatchResponse rerouteTrip(@PathVariable String tripId) {
        AuthenticatedUser user = currentUser();
        requireControl(user, "Only control can reroute trips.");
        Trip trip = store.findTrip(tripId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Trip not found."));
        Incident incident = store.findIncident(trip.incidentId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Incident not found."));
        Ambulance ambulance = store.findAmbulance(trip.ambulanceId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Ambulance not found."));

        DispatchDecision decision = dispatchEngine.rerouteHospital(
                incident,
                ambulanceLocationProjection.applyTo(ambulance),
                trip.hospitalId(),
                store.hospitals()
        );
        Trip updatedTrip = store.rerouteTrip(
                trip.id(),
                decision.hospital().id(),
                decision.winningScore(),
                decision.alternatives()
        );

        Incident updatedIncident = store.findIncident(incident.id()).orElseThrow();
        Ambulance updatedAmbulance = ambulanceLocationProjection.applyTo(store.findAmbulance(ambulance.id()).orElseThrow());
        Hospital updatedHospital = store.findHospital(decision.hospital().id()).orElseThrow();

        auditService.allowed(
                user,
                "trip.reroute",
                "Trip",
                updatedTrip.id(),
                "Trip rerouted.",
                Map.of("incidentId", incident.id(), "ambulanceId", updatedTrip.ambulanceId(), "hospitalId", updatedTrip.hospitalId())
        );

        return new DispatchResponse(
                incidentView(updatedIncident, user),
                updatedAmbulance,
                updatedHospital,
                updatedTrip,
                decision.winningScore(),
                decision.alternatives()
        );
    }

    @PostMapping("/demo/reset")
    public void resetDemo() {
        AuthenticatedUser user = currentUser();
        requireControl(user, "Only control can reset demo data.");
        store.reset();
        ambulanceLocationProjection.clear();
        auditService.allowed(
                user,
                "demo.reset",
                "Demo",
                "seed-data",
                "Demo data reset.",
                Map.of()
        );
    }

    @ExceptionHandler(NoDispatchCandidateException.class)
    @ResponseStatus(HttpStatus.CONFLICT)
    public ErrorResponse handleNoCandidate(NoDispatchCandidateException exception) {
        return ErrorResponse.of(HttpStatus.CONFLICT, exception.getMessage());
    }

    @ExceptionHandler(IllegalStateException.class)
    @ResponseStatus(HttpStatus.CONFLICT)
    public ErrorResponse handleIllegalState(IllegalStateException exception) {
        return ErrorResponse.of(HttpStatus.CONFLICT, exception.getMessage());
    }

    private AuthenticatedUser currentUser() {
        return currentUserService.currentUser();
    }

    private List<Incident> visibleIncidents(AuthenticatedUser user) {
        List<Incident> incidents = store.incidents();
        return switch (user.role()) {
            case CONTROL -> incidents;
            case PATIENT -> incidents.stream()
                    .filter(incident -> incident.requesterUserId().equals(user.username()))
                    .toList();
            case DRIVER, HOSPITAL -> {
                Set<String> incidentIds = visibleTrips(user).stream()
                        .map(Trip::incidentId)
                        .collect(Collectors.toSet());
                yield incidents.stream()
                        .filter(incident -> incidentIds.contains(incident.id()))
                        .toList();
            }
        };
    }

    private List<Trip> visibleTrips(AuthenticatedUser user) {
        List<Trip> trips = store.trips();
        return switch (user.role()) {
            case CONTROL -> trips;
            case PATIENT -> {
                Set<String> ownedIncidentIds = store.incidents().stream()
                        .filter(incident -> incident.requesterUserId().equals(user.username()))
                        .map(Incident::id)
                        .collect(Collectors.toSet());
                yield trips.stream()
                        .filter(trip -> ownedIncidentIds.contains(trip.incidentId()))
                        .toList();
            }
            case DRIVER -> trips.stream()
                    .filter(trip -> trip.ambulanceId().equals(user.ambulanceId()))
                    .toList();
            case HOSPITAL -> trips.stream()
                    .filter(trip -> trip.hospitalId().equals(user.hospitalId()))
                    .toList();
        };
    }

    private List<Ambulance> visibleAmbulances(AuthenticatedUser user) {
        List<Ambulance> ambulances = ambulanceLocationProjection.applyTo(store.ambulances());
        if (user.isControl()) {
            return ambulances;
        }
        if (user.role() == UserRole.DRIVER) {
            return ambulances.stream()
                    .filter(ambulance -> ambulance.id().equals(user.ambulanceId()))
                    .toList();
        }
        Set<String> ambulanceIds = visibleTrips(user).stream()
                .map(Trip::ambulanceId)
                .collect(Collectors.toSet());
        if (user.role() == UserRole.PATIENT) {
            return ambulances.stream()
                    .filter(ambulance -> ambulance.status() == AmbulanceStatus.AVAILABLE || ambulanceIds.contains(ambulance.id()))
                    .toList();
        }
        return ambulances.stream()
                .filter(ambulance -> ambulanceIds.contains(ambulance.id()))
                .toList();
    }

    private List<Hospital> visibleHospitals(AuthenticatedUser user) {
        List<Hospital> hospitals = store.hospitals();
        if (user.isControl()) {
            return hospitals;
        }
        if (user.role() == UserRole.HOSPITAL) {
            return hospitals.stream()
                    .filter(hospital -> hospital.id().equals(user.hospitalId()))
                    .toList();
        }
        Set<String> hospitalIds = visibleTrips(user).stream()
                .map(Trip::hospitalId)
                .collect(Collectors.toSet());
        if (user.role() == UserRole.DRIVER) {
            return hospitals.stream()
                    .filter(hospital -> hospital.hasCapacity() || hospitalIds.contains(hospital.id()))
                    .toList();
        }
        if (user.role() == UserRole.PATIENT) {
            return hospitals.stream()
                    .filter(hospital -> hospital.hasCapacity() || hospitalIds.contains(hospital.id()))
                    .toList();
        }
        return hospitals.stream()
                .filter(hospital -> hospitalIds.contains(hospital.id()))
                .toList();
    }

    private IncidentView incidentView(Incident incident, AuthenticatedUser user) {
        if (user.isControl() || incident.requesterUserId().equals(user.username())) {
            return IncidentView.full(incident);
        }
        return IncidentView.operational(incident);
    }

    private void requireControl(AuthenticatedUser user, String message) {
        if (!user.isControl()) {
            throw forbidden(user, "control.access", "Endpoint", "control-only", message);
        }
    }

    private ResponseStatusException forbidden(
            AuthenticatedUser user,
            String action,
            String resourceType,
            String resourceId,
            String reason
    ) {
        auditService.denied(user, action, resourceType, resourceId, reason, Map.of());
        return new ResponseStatusException(HttpStatus.FORBIDDEN, reason);
    }

    private NotificationRole notificationRole(AuthenticatedUser user) {
        return NotificationRole.valueOf(user.role().name());
    }

    private NotificationRole parseRole(String role) {
        try {
            return NotificationRole.valueOf(role.toUpperCase());
        } catch (IllegalArgumentException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unsupported notification role.");
        }
    }

    private double latestOptimizationImprovement(List<SimulationResult> simulations) {
        return simulations.stream()
                .flatMap(simulation -> simulation.strategyResults().stream())
                .filter(result -> result.improvementPercent() > 0)
                .findFirst()
                .map(result -> Math.round(result.improvementPercent() * 10.0) / 10.0)
                .orElse(0.0);
    }
}
