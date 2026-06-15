package com.lifeline.store;

import com.lifeline.dispatch.CandidateScore;
import com.lifeline.domain.Ambulance;
import com.lifeline.domain.DispatchAuditRecord;
import com.lifeline.domain.EmergencyCondition;
import com.lifeline.domain.Hospital;
import com.lifeline.domain.Incident;
import com.lifeline.domain.IncidentPriority;
import com.lifeline.domain.Location;
import com.lifeline.domain.OutboxEvent;
import com.lifeline.domain.Trip;

import java.util.List;
import java.util.Optional;

public interface LifeLineStore {
    List<Ambulance> ambulances();

    List<Hospital> hospitals();

    List<Incident> incidents();

    List<Trip> trips();

    List<DispatchAuditRecord> dispatchDecisions();

    List<OutboxEvent> outboxEvents();

    Optional<Incident> findIncident(String id);

    Optional<Ambulance> findAmbulance(String id);

    Optional<Hospital> findHospital(String id);

    Incident createIncident(
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

    void reset();
}

