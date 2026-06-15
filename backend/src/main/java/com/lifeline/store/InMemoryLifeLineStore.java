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
import com.lifeline.domain.OutboxEvent;
import com.lifeline.domain.Trip;
import com.lifeline.domain.TripStatus;
import jakarta.annotation.PostConstruct;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.ArrayList;
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

    @PostConstruct
    public void seed() {
        reset();
    }

    public synchronized void reset() {
        ambulances.clear();
        hospitals.clear();
        incidents.clear();
        trips.clear();

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

        saveIncident(new Incident("INC-301", "Ananya Rao", "+91-90000-10001", EmergencyCondition.CARDIAC, IncidentPriority.CRITICAL, new Location(12.9458, 77.6309), Instant.now().minusSeconds(180), IncidentStatus.NEW));
        saveIncident(new Incident("INC-302", "Rohan Mehta", "+91-90000-10002", EmergencyCondition.TRAUMA, IncidentPriority.HIGH, new Location(12.9166, 77.6101), Instant.now().minusSeconds(90), IncidentStatus.NEW));
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
        return List.of();
    }

    @Override
    public synchronized List<OutboxEvent> outboxEvents() {
        return List.of();
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

    public synchronized Incident saveIncident(Incident incident) {
        incidents.put(incident.id(), incident);
        return incident;
    }

    @Override
    public synchronized Incident createIncident(
            String patientName,
            String phone,
            EmergencyCondition condition,
            IncidentPriority priority,
            Location location
    ) {
        String id = "INC-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        Incident incident = new Incident(id, patientName, phone, condition, priority, location, Instant.now(), IncidentStatus.NEW);
        incidents.put(id, incident);
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
        return trip;
    }

    private void addAmbulance(Ambulance ambulance) {
        ambulances.put(ambulance.id(), ambulance);
    }

    private void addHospital(Hospital hospital) {
        hospitals.put(hospital.id(), hospital);
    }
}
