package com.lifeline.store;

import com.lifeline.dispatch.CandidateScore;
import com.lifeline.domain.Ambulance;
import com.lifeline.domain.DispatchAuditRecord;
import com.lifeline.domain.EmergencyCondition;
import com.lifeline.domain.Hospital;
import com.lifeline.domain.Incident;
import com.lifeline.domain.IncidentPriority;
import com.lifeline.domain.Location;
import com.lifeline.domain.Notification;
import com.lifeline.domain.NotificationRole;
import com.lifeline.domain.OutboxEvent;
import com.lifeline.domain.SecurityAuditEvent;
import com.lifeline.domain.Trip;
import com.lifeline.domain.TripStatus;
import com.lifeline.simulation.SimulationResult;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface LifeLineStore {
    List<Ambulance> ambulances();

    List<Hospital> hospitals();

    List<Incident> incidents();

    List<Trip> trips();

    List<DispatchAuditRecord> dispatchDecisions();

    List<OutboxEvent> outboxEvents();

    List<Notification> notifications(NotificationRole role);

    List<SimulationResult> simulations();

    List<SecurityAuditEvent> securityAuditEvents(int limit);

    List<OutboxEvent> pendingOutboxEvents(int limit);

    List<OutboxEvent> claimReadyOutboxEvents(int limit, Instant claimedAt, Instant nextAttemptAt);

    int pendingOutboxEventCount();

    int notificationBacklog();

    Optional<Incident> findIncident(String id);

    Optional<Ambulance> findAmbulance(String id);

    Optional<Hospital> findHospital(String id);

    Optional<Trip> findTrip(String id);

    Optional<SimulationResult> findSimulation(String id);

    default Incident createIncident(
            String patientName,
            String phone,
            EmergencyCondition condition,
            IncidentPriority priority,
            Location location
    ) {
        return createIncident("patient.demo", patientName, phone, condition, priority, location);
    }

    Incident createIncident(
            String requesterUserId,
            String patientName,
            String phone,
            EmergencyCondition condition,
            IncidentPriority priority,
            Location location
    );

    Trip commitAssignment(
            String incidentId,
            String ambulanceId,
            String hospitalId,
            CandidateScore winningScore,
            List<CandidateScore> alternatives
    );

    Trip updateTripStatus(String tripId, TripStatus status);

    Hospital updateHospitalCapacity(String hospitalId, int availableBeds);

    Trip rerouteTrip(
            String tripId,
            String hospitalId,
            CandidateScore winningScore,
            List<CandidateScore> alternatives
    );

    int publishPendingOutboxEvents(int limit);

    void markOutboxEventPublished(String eventId, Instant publishedAt);

    void markOutboxEventFailed(String eventId, String failureReason);

    Notification addNotification(Notification notification);

    Notification acknowledgeNotification(String notificationId);

    SimulationResult saveSimulationResult(SimulationResult result);

    SecurityAuditEvent addSecurityAuditEvent(SecurityAuditEvent event);

    void reset();
}
