package com.lifeline.api;

import com.lifeline.dispatch.DispatchDecision;
import com.lifeline.dispatch.DispatchEngine;
import com.lifeline.dispatch.NoDispatchCandidateException;
import com.lifeline.domain.Ambulance;
import com.lifeline.domain.AmbulanceStatus;
import com.lifeline.domain.DispatchAuditRecord;
import com.lifeline.domain.Hospital;
import com.lifeline.domain.Incident;
import com.lifeline.domain.IncidentStatus;
import com.lifeline.domain.Location;
import com.lifeline.domain.OutboxEvent;
import com.lifeline.domain.Trip;
import com.lifeline.outbox.OutboxProcessor;
import com.lifeline.outbox.OutboxPublishResult;
import com.lifeline.outbox.OutboxSummary;
import com.lifeline.outbox.OutboxSummaryService;
import com.lifeline.store.LifeLineStore;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.List;

@RestController
@RequestMapping("/api")
@CrossOrigin(origins = "*")
public class LifeLineController {
    private final LifeLineStore store;
    private final DispatchEngine dispatchEngine;
    private final OutboxProcessor outboxProcessor;
    private final OutboxSummaryService outboxSummaryService;

    public LifeLineController(
            LifeLineStore store,
            DispatchEngine dispatchEngine,
            OutboxProcessor outboxProcessor,
            OutboxSummaryService outboxSummaryService
    ) {
        this.store = store;
        this.dispatchEngine = dispatchEngine;
        this.outboxProcessor = outboxProcessor;
        this.outboxSummaryService = outboxSummaryService;
    }

    @GetMapping("/ambulances")
    public List<Ambulance> ambulances() {
        return store.ambulances();
    }

    @GetMapping("/hospitals")
    public List<Hospital> hospitals() {
        return store.hospitals();
    }

    @GetMapping("/incidents")
    public List<Incident> incidents() {
        return store.incidents();
    }

    @GetMapping("/trips")
    public List<Trip> trips() {
        return store.trips();
    }

    @GetMapping("/dispatch-decisions")
    public List<DispatchAuditRecord> dispatchDecisions() {
        return store.dispatchDecisions();
    }

    @GetMapping("/outbox-events")
    public List<OutboxEvent> outboxEvents() {
        return store.outboxEvents();
    }

    @GetMapping("/outbox-events/pending")
    public List<OutboxEvent> pendingOutboxEvents() {
        return store.pendingOutboxEvents(50);
    }

    @GetMapping("/outbox-events/summary")
    public OutboxSummary outboxSummary() {
        return outboxSummaryService.summarize(store.outboxEvents(), Instant.now());
    }

    @GetMapping("/metrics")
    public MetricsResponse metrics() {
        List<Incident> incidents = store.incidents();
        List<Ambulance> ambulances = store.ambulances();
        List<Hospital> hospitals = store.hospitals();
        List<Trip> trips = store.trips();

        double averageBedAvailability = hospitals.stream()
                .mapToDouble(hospital -> hospital.totalBeds() == 0 ? 0 : (double) hospital.availableBeds() / hospital.totalBeds())
                .average()
                .orElse(0);
        List<OutboxEvent> outboxEvents = store.outboxEvents();

        return new MetricsResponse(
                (int) incidents.stream().filter(incident -> incident.status() == IncidentStatus.NEW).count(),
                (int) ambulances.stream().filter(ambulance -> ambulance.status() == AmbulanceStatus.AVAILABLE).count(),
                trips.size(),
                (int) hospitals.stream().filter(Hospital::hasCapacity).count(),
                Math.round(averageBedAvailability * 1000.0) / 10.0,
                (int) outboxEvents.stream().filter(event -> event.publishedAt() == null).count(),
                (int) outboxEvents.stream().filter(event -> event.publishedAt() != null).count()
        );
    }

    @PostMapping("/outbox-events/publish")
    public OutboxPublishResult publishOutboxEvents() {
        return outboxProcessor.publishPendingNow();
    }

    @PostMapping("/incidents")
    @ResponseStatus(HttpStatus.CREATED)
    public Incident createIncident(@Valid @RequestBody CreateIncidentRequest request) {
        return store.createIncident(
                request.patientName(),
                request.phone(),
                request.condition(),
                request.priority(),
                new Location(request.latitude(), request.longitude())
        );
    }

    @PostMapping("/dispatch")
    public DispatchResponse dispatch(@Valid @RequestBody DispatchRequest request) {
        Incident incident = store.findIncident(request.incidentId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Incident not found."));

        DispatchDecision decision = dispatchEngine.decide(incident, store.ambulances(), store.hospitals());
        Trip trip = store.commitAssignment(
                incident.id(),
                decision.ambulance().id(),
                decision.hospital().id(),
                decision.winningScore(),
                decision.alternatives()
        );

        Incident updatedIncident = store.findIncident(incident.id()).orElseThrow();
        Ambulance updatedAmbulance = store.findAmbulance(decision.ambulance().id()).orElseThrow();
        Hospital updatedHospital = store.findHospital(decision.hospital().id()).orElseThrow();

        return new DispatchResponse(
                updatedIncident,
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
        return store.updateTripStatus(tripId, request.status());
    }

    @PostMapping("/hospitals/{hospitalId}/capacity")
    public Hospital updateHospitalCapacity(
            @PathVariable String hospitalId,
            @Valid @RequestBody UpdateHospitalCapacityRequest request
    ) {
        return store.updateHospitalCapacity(hospitalId, request.availableBeds());
    }

    @PostMapping("/trips/{tripId}/reroute")
    public DispatchResponse rerouteTrip(@PathVariable String tripId) {
        Trip trip = store.findTrip(tripId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Trip not found."));
        Incident incident = store.findIncident(trip.incidentId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Incident not found."));
        Ambulance ambulance = store.findAmbulance(trip.ambulanceId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Ambulance not found."));

        DispatchDecision decision = dispatchEngine.rerouteHospital(incident, ambulance, trip.hospitalId(), store.hospitals());
        Trip updatedTrip = store.rerouteTrip(
                trip.id(),
                decision.hospital().id(),
                decision.winningScore(),
                decision.alternatives()
        );

        Incident updatedIncident = store.findIncident(incident.id()).orElseThrow();
        Ambulance updatedAmbulance = store.findAmbulance(ambulance.id()).orElseThrow();
        Hospital updatedHospital = store.findHospital(decision.hospital().id()).orElseThrow();

        return new DispatchResponse(
                updatedIncident,
                updatedAmbulance,
                updatedHospital,
                updatedTrip,
                decision.winningScore(),
                decision.alternatives()
        );
    }

    @PostMapping("/demo/reset")
    public void resetDemo() {
        store.reset();
    }

    @ExceptionHandler(NoDispatchCandidateException.class)
    @ResponseStatus(HttpStatus.CONFLICT)
    public ErrorResponse handleNoCandidate(NoDispatchCandidateException exception) {
        return new ErrorResponse(exception.getMessage());
    }

    @ExceptionHandler(IllegalStateException.class)
    @ResponseStatus(HttpStatus.CONFLICT)
    public ErrorResponse handleIllegalState(IllegalStateException exception) {
        return new ErrorResponse(exception.getMessage());
    }
}
