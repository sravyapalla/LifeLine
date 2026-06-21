package com.lifeline.store;

import com.lifeline.dispatch.CandidateScore;
import com.lifeline.domain.Ambulance;
import com.lifeline.domain.AmbulanceStatus;
import com.lifeline.domain.AmbulanceType;
import com.lifeline.domain.DispatchAuditRecord;
import com.lifeline.domain.EmergencyCondition;
import com.lifeline.domain.Hospital;
import com.lifeline.domain.Incident;
import com.lifeline.domain.IncidentPriority;
import com.lifeline.domain.IncidentStatus;
import com.lifeline.domain.Location;
import com.lifeline.domain.Notification;
import com.lifeline.domain.NotificationRole;
import com.lifeline.domain.OutboxEvent;
import com.lifeline.domain.SecurityAuditEvent;
import com.lifeline.domain.Trip;
import com.lifeline.domain.TripStatus;
import com.lifeline.simulation.SimulationResult;
import jakarta.annotation.PostConstruct;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

@Component
@Profile("memory")
public class InMemoryLifeLineStore implements LifeLineStore {
    private final Map<String, Ambulance> ambulances = new LinkedHashMap<>();
    private final Map<String, Hospital> hospitals = new LinkedHashMap<>();
    private final Map<String, Incident> incidents = new LinkedHashMap<>();
    private final Map<String, Trip> trips = new LinkedHashMap<>();
    private final List<DispatchAuditRecord> dispatchDecisions = new ArrayList<>();
    private final List<OutboxEvent> outboxEvents = new ArrayList<>();
    private final List<Notification> notifications = new ArrayList<>();
    private final List<SimulationResult> simulations = new ArrayList<>();
    private final List<SecurityAuditEvent> securityAuditEvents = new ArrayList<>();

    @PostConstruct
    public void seed() {
        reset();
    }

    public synchronized void reset() {
        ambulances.clear();
        hospitals.clear();
        incidents.clear();
        trips.clear();
        dispatchDecisions.clear();
        outboxEvents.clear();
        notifications.clear();
        simulations.clear();

        addAmbulance(new Ambulance("AMB-101", "Aster Alpha", AmbulanceType.ALS, AmbulanceStatus.AVAILABLE, new Location(12.9719, 77.6412), "Indiranagar"));
        addAmbulance(new Ambulance("AMB-102", "Pulse Bravo", AmbulanceType.BLS, AmbulanceStatus.AVAILABLE, new Location(12.9352, 77.6245), "Koramangala"));
        addAmbulance(new Ambulance("AMB-103", "Rescue Charlie", AmbulanceType.ICU, AmbulanceStatus.AVAILABLE, new Location(13.0118, 77.5549), "Malleshwaram"));
        addAmbulance(new Ambulance("AMB-104", "Rapid Delta", AmbulanceType.ALS, AmbulanceStatus.AVAILABLE, new Location(12.9141, 77.6101), "BTM Layout"));
        addAmbulance(new Ambulance("AMB-105", "Care Echo", AmbulanceType.BLS, AmbulanceStatus.AVAILABLE, new Location(12.9987, 77.5924), "Hebbal"));
        addAmbulance(new Ambulance("AMB-106", "Life Foxtrot", AmbulanceType.ALS, AmbulanceStatus.OFFLINE, new Location(12.9698, 77.7500), "Whitefield"));

        addHospital(new Hospital("HOS-201", "Narayana Cardiac Centre", new Location(12.9384, 77.6906), Set.of(EmergencyCondition.CARDIAC, EmergencyCondition.STROKE), 42, 7, 0.94));
        addHospital(new Hospital("HOS-202", "Manipal Emergency Hospital", new Location(12.9592, 77.6489), Set.of(EmergencyCondition.TRAUMA, EmergencyCondition.CARDIAC, EmergencyCondition.GENERAL), 65, 14, 0.9));
        addHospital(new Hospital("HOS-203", "Victoria Trauma Institute", new Location(12.9634, 77.5739), Set.of(EmergencyCondition.TRAUMA, EmergencyCondition.GENERAL), 58, 4, 0.86));
        addHospital(new Hospital("HOS-204", "Cloudnine Pediatric ER", new Location(12.9336, 77.6234), Set.of(EmergencyCondition.PEDIATRIC, EmergencyCondition.GENERAL), 35, 9, 0.89));
        addHospital(new Hospital("HOS-205", "Baptist North Care", new Location(13.0358, 77.5891), Set.of(EmergencyCondition.STROKE, EmergencyCondition.GENERAL), 44, 11, 0.84));

        saveIncident(new Incident("INC-301", "patient.demo", "Ananya Rao", "+91-90000-10001", EmergencyCondition.CARDIAC, IncidentPriority.CRITICAL, new Location(12.9458, 77.6309), Instant.now().minusSeconds(180), IncidentStatus.NEW));
        saveIncident(new Incident("INC-302", "patient.demo", "Rohan Mehta", "+91-90000-10002", EmergencyCondition.TRAUMA, IncidentPriority.HIGH, new Location(12.9166, 77.6101), Instant.now().minusSeconds(90), IncidentStatus.NEW));
    }

    @Override
    public synchronized List<Ambulance> ambulances() {
        return new ArrayList<>(ambulances.values());
    }

    @Override
    public synchronized List<Hospital> hospitals() {
        return new ArrayList<>(hospitals.values());
    }

    @Override
    public synchronized List<Incident> incidents() {
        return new ArrayList<>(incidents.values());
    }

    @Override
    public synchronized List<Trip> trips() {
        return new ArrayList<>(trips.values());
    }

    @Override
    public synchronized List<DispatchAuditRecord> dispatchDecisions() {
        return new ArrayList<>(dispatchDecisions);
    }

    @Override
    public synchronized List<OutboxEvent> outboxEvents() {
        return new ArrayList<>(outboxEvents);
    }

    @Override
    public synchronized List<Notification> notifications(NotificationRole role) {
        return notifications.stream()
                .filter(notification -> notification.role() == role)
                .sorted(Comparator.comparing(Notification::createdAt).reversed())
                .toList();
    }

    @Override
    public synchronized List<SimulationResult> simulations() {
        return simulations.stream()
                .sorted(Comparator.comparing(SimulationResult::createdAt).reversed())
                .toList();
    }

    @Override
    public synchronized List<SecurityAuditEvent> securityAuditEvents(int limit) {
        return securityAuditEvents.stream()
                .sorted(Comparator.comparing(SecurityAuditEvent::createdAt).reversed())
                .limit(limit)
                .toList();
    }

    @Override
    public synchronized List<OutboxEvent> pendingOutboxEvents(int limit) {
        return outboxEvents.stream()
                .filter(event -> event.publishedAt() == null)
                .limit(limit)
                .toList();
    }

    @Override
    public synchronized List<OutboxEvent> claimReadyOutboxEvents(int limit, Instant claimedAt, Instant nextAttemptAt) {
        List<OutboxEvent> claimed = outboxEvents.stream()
                .filter(event -> event.isReadyForPublish(claimedAt))
                .sorted(Comparator.comparing(OutboxEvent::createdAt))
                .limit(limit)
                .map(event -> event.claimedAt(claimedAt, nextAttemptAt))
                .toList();

        for (OutboxEvent event : claimed) {
            replaceOutboxEvent(event);
        }
        return claimed;
    }

    @Override
    public synchronized int pendingOutboxEventCount() {
        return (int) outboxEvents.stream()
                .filter(event -> event.publishedAt() == null)
                .count();
    }

    @Override
    public synchronized int notificationBacklog() {
        return (int) notifications.stream()
                .filter(Notification::unread)
                .count();
    }

    @Override
    public synchronized Optional<Incident> findIncident(String id) {
        return Optional.ofNullable(incidents.get(id));
    }

    @Override
    public synchronized Optional<Ambulance> findAmbulance(String id) {
        return Optional.ofNullable(ambulances.get(id));
    }

    @Override
    public synchronized Optional<Hospital> findHospital(String id) {
        return Optional.ofNullable(hospitals.get(id));
    }

    @Override
    public synchronized Optional<Trip> findTrip(String id) {
        return Optional.ofNullable(trips.get(id));
    }

    @Override
    public synchronized Optional<SimulationResult> findSimulation(String id) {
        return simulations.stream()
                .filter(simulation -> simulation.id().equals(id))
                .findFirst();
    }

    public synchronized Incident saveIncident(Incident incident) {
        incidents.put(incident.id(), incident);
        return incident;
    }

    @Override
    public synchronized Incident createIncident(
            String requesterUserId,
            String patientName,
            String phone,
            EmergencyCondition condition,
            IncidentPriority priority,
            Location location
    ) {
        String id = "INC-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        Instant now = Instant.now();
        Incident incident = new Incident(id, requesterUserId, patientName, phone, condition, priority, location, now, IncidentStatus.NEW);
        incidents.put(id, incident);
        appendOutbox("Incident", id, "incident.created", "{\"incidentId\":\"%s\"}".formatted(id), now);
        return incident;
    }

    @Override
    public synchronized Trip commitAssignment(
            String incidentId,
            String ambulanceId,
            String hospitalId,
            CandidateScore score,
            List<CandidateScore> alternatives
    ) {
        Incident incident = incidents.get(incidentId);
        Ambulance ambulance = ambulances.get(ambulanceId);
        Hospital hospital = hospitals.get(hospitalId);

        if (incident == null || ambulance == null || hospital == null) {
            throw new IllegalStateException("Cannot reserve missing incident, ambulance, or hospital.");
        }
        if (incident.status() != IncidentStatus.NEW) {
            throw new IllegalStateException("Incident is no longer dispatchable.");
        }
        if (ambulance.status() != AmbulanceStatus.AVAILABLE) {
            throw new IllegalStateException("Ambulance is no longer available.");
        }
        if (!hospital.hasCapacity()) {
            throw new IllegalStateException("Hospital no longer has available beds.");
        }

        ambulances.put(ambulanceId, ambulance.withStatus(AmbulanceStatus.RESERVED));
        hospitals.put(hospitalId, hospital.reserveOneBed());
        incidents.put(incidentId, incident.withStatus(IncidentStatus.ASSIGNED));

        Trip trip = new Trip(
                "TRIP-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase(),
                incidentId,
                ambulanceId,
                hospitalId,
                score.pickupEtaMinutes(),
                score.hospitalEtaMinutes(),
                score.totalCost(),
                Instant.now(),
                TripStatus.RESERVED
        );
        trips.put(trip.id(), trip);
        dispatchDecisions.addFirst(new DispatchAuditRecord(
                "DEC-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase(),
                incidentId,
                ambulanceId,
                hospitalId,
                score.pickupEtaMinutes(),
                score.hospitalEtaMinutes(),
                score.hospitalLoad(),
                score.qualityPenalty(),
                score.typePenalty(),
                score.totalCost(),
                score.explanation(),
                trip.createdAt()
        ));
        appendOutbox("Dispatch", trip.id(), "dispatch.reserved", "{\"tripId\":\"%s\"}".formatted(trip.id()), trip.createdAt());
        return trip;
    }

    @Override
    public synchronized Trip updateTripStatus(String tripId, TripStatus status) {
        Trip trip = trips.get(tripId);
        if (trip == null) {
            throw new IllegalStateException("Trip not found.");
        }

        Trip updatedTrip = trip.withStatus(status);
        trips.put(tripId, updatedTrip);

        Ambulance ambulance = ambulances.get(trip.ambulanceId());
        Incident incident = incidents.get(trip.incidentId());

        if (ambulance != null) {
            AmbulanceStatus nextAmbulanceStatus = switch (status) {
                case RESERVED -> AmbulanceStatus.RESERVED;
                case EN_ROUTE_PATIENT, EN_ROUTE_HOSPITAL -> AmbulanceStatus.ON_TRIP;
                case COMPLETED, CANCELLED -> AmbulanceStatus.AVAILABLE;
            };
            ambulances.put(ambulance.id(), ambulance.withStatus(nextAmbulanceStatus));
        }

        if (incident != null && status == TripStatus.COMPLETED) {
            incidents.put(incident.id(), incident.withStatus(IncidentStatus.COMPLETED));
        } else if (incident != null && status == TripStatus.CANCELLED) {
            incidents.put(incident.id(), incident.withStatus(IncidentStatus.CANCELLED));
            releaseHospitalBed(trip.hospitalId());
        }

        appendOutbox("Trip", tripId, "trip.status_changed", "{\"tripId\":\"%s\",\"status\":\"%s\"}".formatted(tripId, status), Instant.now());
        return updatedTrip;
    }

    @Override
    public synchronized Hospital updateHospitalCapacity(String hospitalId, int availableBeds) {
        Hospital hospital = hospitals.get(hospitalId);
        if (hospital == null) {
            throw new IllegalStateException("Hospital not found.");
        }
        if (availableBeds < 0 || availableBeds > hospital.totalBeds()) {
            throw new IllegalStateException("Available beds must be between 0 and total beds.");
        }

        Hospital updatedHospital = hospital.withAvailableBeds(availableBeds);
        hospitals.put(hospitalId, updatedHospital);
        appendOutbox("Hospital", hospitalId, "hospital.capacity_changed", "{\"hospitalId\":\"%s\",\"availableBeds\":%d}".formatted(hospitalId, availableBeds), Instant.now());
        return updatedHospital;
    }

    @Override
    public synchronized Trip rerouteTrip(
            String tripId,
            String hospitalId,
            CandidateScore winningScore,
            List<CandidateScore> alternatives
    ) {
        Trip trip = trips.get(tripId);
        Hospital hospital = hospitals.get(hospitalId);
        Incident incident = trip == null ? null : incidents.get(trip.incidentId());

        if (trip == null || hospital == null || incident == null) {
            throw new IllegalStateException("Cannot reroute missing trip, hospital, or incident.");
        }
        if (trip.status() == TripStatus.COMPLETED || trip.status() == TripStatus.CANCELLED) {
            throw new IllegalStateException("Completed or cancelled trips cannot be rerouted.");
        }
        if (!hospital.canTreat(incident.condition())) {
            throw new IllegalStateException("Target hospital cannot treat this condition.");
        }
        if (!hospital.hasCapacity()) {
            throw new IllegalStateException("Target hospital has no capacity.");
        }

        hospitals.put(hospitalId, hospital.reserveOneBed());
        Trip updatedTrip = trip.withHospital(hospitalId, winningScore.hospitalEtaMinutes(), winningScore.totalCost());
        trips.put(tripId, updatedTrip);
        dispatchDecisions.addFirst(new DispatchAuditRecord(
                "DEC-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase(),
                trip.incidentId(),
                trip.ambulanceId(),
                hospitalId,
                winningScore.pickupEtaMinutes(),
                winningScore.hospitalEtaMinutes(),
                winningScore.hospitalLoad(),
                winningScore.qualityPenalty(),
                winningScore.typePenalty(),
                winningScore.totalCost(),
                "Reroute: " + winningScore.explanation(),
                Instant.now()
        ));
        appendOutbox("Trip", tripId, "trip.rerouted", "{\"tripId\":\"%s\",\"hospitalId\":\"%s\"}".formatted(tripId, hospitalId), Instant.now());
        return updatedTrip;
    }

    @Override
    public synchronized int publishPendingOutboxEvents(int limit) {
        int published = 0;
        Instant now = Instant.now();
        List<OutboxEvent> claimed = claimReadyOutboxEvents(limit, now, now);
        for (OutboxEvent event : claimed) {
            markOutboxEventPublished(event.id(), now);
            published += 1;
        }
        return published;
    }

    @Override
    public synchronized void markOutboxEventPublished(String eventId, Instant publishedAt) {
        outboxEvents.stream()
                .filter(event -> event.id().equals(eventId) && event.publishedAt() == null)
                .findFirst()
                .map(event -> event.publishedAt(publishedAt))
                .ifPresent(this::replaceOutboxEvent);
    }

    @Override
    public synchronized void markOutboxEventFailed(String eventId, String failureReason) {
        outboxEvents.stream()
                .filter(event -> event.id().equals(eventId) && event.publishedAt() == null)
                .findFirst()
                .map(event -> event.failedWith(failureReason))
                .ifPresent(this::replaceOutboxEvent);
    }

    @Override
    public synchronized Notification addNotification(Notification notification) {
        notifications.addFirst(notification);
        return notification;
    }

    @Override
    public synchronized Notification acknowledgeNotification(String notificationId) {
        Instant acknowledgedAt = Instant.now();
        for (int index = 0; index < notifications.size(); index += 1) {
            Notification notification = notifications.get(index);
            if (notification.id().equals(notificationId)) {
                Notification updated = notification.acknowledged(acknowledgedAt);
                notifications.set(index, updated);
                return updated;
            }
        }
        throw new IllegalStateException("Notification not found.");
    }

    @Override
    public synchronized SimulationResult saveSimulationResult(SimulationResult result) {
        simulations.addFirst(result);
        return result;
    }

    @Override
    public synchronized SecurityAuditEvent addSecurityAuditEvent(SecurityAuditEvent event) {
        securityAuditEvents.addFirst(event);
        return event;
    }

    private void addAmbulance(Ambulance ambulance) {
        ambulances.put(ambulance.id(), ambulance);
    }

    private void addHospital(Hospital hospital) {
        hospitals.put(hospital.id(), hospital);
    }

    private void releaseHospitalBed(String hospitalId) {
        Hospital hospital = hospitals.get(hospitalId);
        if (hospital != null) {
            hospitals.put(hospitalId, hospital.withAvailableBeds(Math.min(hospital.availableBeds() + 1, hospital.totalBeds())));
        }
    }

    private void appendOutbox(String aggregateType, String aggregateId, String eventType, String payload, Instant now) {
        outboxEvents.addFirst(new OutboxEvent(
                "EVT-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase(),
                aggregateType,
                aggregateId,
                eventType,
                payload,
                now,
                null,
                0,
                null,
                null,
                null
        ));
    }

    private void replaceOutboxEvent(OutboxEvent updated) {
        for (int index = 0; index < outboxEvents.size(); index += 1) {
            if (outboxEvents.get(index).id().equals(updated.id())) {
                outboxEvents.set(index, updated);
                return;
            }
        }
    }
}
