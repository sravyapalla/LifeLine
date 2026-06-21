package com.lifeline.store;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
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
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

@Repository
@Profile("!memory")
public class JdbcLifeLineStore implements LifeLineStore {
    private final JdbcTemplate jdbc;
    private final ObjectMapper objectMapper;

    public JdbcLifeLineStore(JdbcTemplate jdbc, ObjectMapper objectMapper) {
        this.jdbc = jdbc;
        this.objectMapper = objectMapper;
    }

    @Override
    public List<Ambulance> ambulances() {
        return jdbc.query("""
                SELECT id, call_sign, type, status, latitude, longitude, base_station
                FROM ambulances
                ORDER BY id
                """, ambulanceMapper());
    }

    @Override
    public List<Hospital> hospitals() {
        Map<String, Set<EmergencyCondition>> specialties = allHospitalSpecialties();
        return jdbc.query("""
                SELECT id, name, latitude, longitude, total_beds, available_beds, quality_score
                FROM hospitals
                ORDER BY id
                """, hospitalMapper(specialties));
    }

    @Override
    public List<Incident> incidents() {
        return jdbc.query("""
                SELECT id, patient_name, phone, condition, priority, latitude, longitude, created_at, status
                FROM incidents
                ORDER BY created_at DESC
                """, incidentMapper());
    }

    @Override
    public List<Trip> trips() {
        return jdbc.query("""
                SELECT id, incident_id, ambulance_id, hospital_id, pickup_eta_minutes,
                       hospital_eta_minutes, total_cost, created_at, status
                FROM trips
                ORDER BY created_at DESC
                """, tripMapper());
    }

    @Override
    public List<DispatchAuditRecord> dispatchDecisions() {
        return jdbc.query("""
                SELECT id, incident_id, ambulance_id, hospital_id, pickup_eta_minutes,
                       hospital_eta_minutes, hospital_load, quality_penalty, type_penalty,
                       total_cost, explanation, created_at
                FROM dispatch_decisions
                ORDER BY created_at DESC
                """, dispatchAuditMapper());
    }

    @Override
    public List<OutboxEvent> outboxEvents() {
        return jdbc.query("""
                SELECT id, aggregate_type, aggregate_id, event_type, payload, created_at, published_at
                FROM outbox_events
                ORDER BY created_at DESC
                """, outboxEventMapper());
    }

    @Override
    public List<OutboxEvent> pendingOutboxEvents(int limit) {
        return jdbc.query("""
                SELECT id, aggregate_type, aggregate_id, event_type, payload, created_at, published_at
                FROM outbox_events
                WHERE published_at IS NULL
                ORDER BY created_at
                LIMIT ?
                """, outboxEventMapper(), limit);
    }

    @Override
    public int pendingOutboxEventCount() {
        Integer count = jdbc.queryForObject("""
                SELECT COUNT(*)
                FROM outbox_events
                WHERE published_at IS NULL
                """, Integer.class);
        return count == null ? 0 : count;
    }

    @Override
    public Optional<Incident> findIncident(String id) {
        return queryOptional("""
                SELECT id, patient_name, phone, condition, priority, latitude, longitude, created_at, status
                FROM incidents
                WHERE id = ?
                """, incidentMapper(), id);
    }

    @Override
    public Optional<Ambulance> findAmbulance(String id) {
        return queryOptional("""
                SELECT id, call_sign, type, status, latitude, longitude, base_station
                FROM ambulances
                WHERE id = ?
                """, ambulanceMapper(), id);
    }

    @Override
    public Optional<Hospital> findHospital(String id) {
        Map<String, Set<EmergencyCondition>> specialties = Map.of(id, hospitalSpecialties(id));
        return queryOptional("""
                SELECT id, name, latitude, longitude, total_beds, available_beds, quality_score
                FROM hospitals
                WHERE id = ?
                """, hospitalMapper(specialties), id);
    }

    @Override
    public Optional<Trip> findTrip(String id) {
        return queryOptional("""
                SELECT id, incident_id, ambulance_id, hospital_id, pickup_eta_minutes,
                       hospital_eta_minutes, total_cost, created_at, status
                FROM trips
                WHERE id = ?
                """, tripMapper(), id);
    }

    @Override
    @Transactional
    public Incident createIncident(
            String patientName,
            String phone,
            EmergencyCondition condition,
            IncidentPriority priority,
            Location location
    ) {
        String id = newId("INC");
        Instant now = Instant.now();

        jdbc.update("""
                INSERT INTO incidents (id, patient_name, phone, condition, priority, latitude, longitude, created_at, status)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
                """,
                id,
                patientName,
                phone,
                condition.name(),
                priority.name(),
                location.latitude(),
                location.longitude(),
                Timestamp.from(now),
                IncidentStatus.NEW.name()
        );

        appendOutbox("Incident", id, "incident.created", Map.of(
                "incidentId", id,
                "condition", condition.name(),
                "priority", priority.name()
        ), now);

        return findIncident(id).orElseThrow();
    }

    @Override
    @Transactional
    public Trip commitAssignment(
            String incidentId,
            String ambulanceId,
            String hospitalId,
            CandidateScore winningScore,
            List<CandidateScore> alternatives
    ) {
        Incident incident = lockIncident(incidentId)
                .orElseThrow(() -> new IllegalStateException("Cannot reserve missing incident."));
        Ambulance ambulance = lockAmbulance(ambulanceId)
                .orElseThrow(() -> new IllegalStateException("Cannot reserve missing ambulance."));
        Hospital hospital = lockHospital(hospitalId)
                .orElseThrow(() -> new IllegalStateException("Cannot reserve missing hospital."));

        if (incident.status() != IncidentStatus.NEW) {
            throw new IllegalStateException("Incident is no longer dispatchable.");
        }
        if (ambulance.status() != AmbulanceStatus.AVAILABLE) {
            throw new IllegalStateException("Ambulance is no longer available.");
        }
        if (!ambulance.type().canHandle(requiredAmbulanceType(incident.condition(), incident.priority()))) {
            throw new IllegalStateException("Ambulance capability no longer satisfies the incident.");
        }
        if (!hospital.canTreat(incident.condition())) {
            throw new IllegalStateException("Hospital can no longer treat this condition.");
        }
        if (!hospital.hasCapacity()) {
            throw new IllegalStateException("Hospital no longer has available beds.");
        }

        Instant now = Instant.now();
        String tripId = newId("TRIP");
        String decisionId = newId("DEC");

        jdbc.update("""
                UPDATE ambulances
                SET status = ?, updated_at = ?
                WHERE id = ?
                """, AmbulanceStatus.RESERVED.name(), Timestamp.from(now), ambulanceId);

        jdbc.update("""
                UPDATE hospitals
                SET available_beds = available_beds - 1, updated_at = ?
                WHERE id = ? AND available_beds > 0
                """, Timestamp.from(now), hospitalId);

        jdbc.update("""
                UPDATE incidents
                SET status = ?
                WHERE id = ?
                """, IncidentStatus.ASSIGNED.name(), incidentId);

        jdbc.update("""
                INSERT INTO trips (id, incident_id, ambulance_id, hospital_id, pickup_eta_minutes,
                                   hospital_eta_minutes, total_cost, created_at, status)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
                """,
                tripId,
                incidentId,
                ambulanceId,
                hospitalId,
                winningScore.pickupEtaMinutes(),
                winningScore.hospitalEtaMinutes(),
                winningScore.totalCost(),
                Timestamp.from(now),
                TripStatus.RESERVED.name()
        );

        jdbc.update("""
                INSERT INTO dispatch_decisions (id, incident_id, ambulance_id, hospital_id, pickup_eta_minutes,
                                                hospital_eta_minutes, hospital_load, quality_penalty,
                                                type_penalty, total_cost, explanation, created_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """,
                decisionId,
                incidentId,
                ambulanceId,
                hospitalId,
                winningScore.pickupEtaMinutes(),
                winningScore.hospitalEtaMinutes(),
                winningScore.hospitalLoad(),
                winningScore.qualityPenalty(),
                winningScore.typePenalty(),
                winningScore.totalCost(),
                winningScore.explanation(),
                Timestamp.from(now)
        );

        insertCandidateScores(decisionId, alternatives);

        appendOutbox("Dispatch", tripId, "dispatch.reserved", Map.of(
                "tripId", tripId,
                "incidentId", incidentId,
                "ambulanceId", ambulanceId,
                "hospitalId", hospitalId,
                "decisionId", decisionId,
                "totalCost", winningScore.totalCost()
        ), now);

        return new Trip(
                tripId,
                incidentId,
                ambulanceId,
                hospitalId,
                winningScore.pickupEtaMinutes(),
                winningScore.hospitalEtaMinutes(),
                winningScore.totalCost(),
                now,
                TripStatus.RESERVED
        );
    }

    @Override
    @Transactional
    public Trip updateTripStatus(String tripId, TripStatus status) {
        Trip trip = lockTrip(tripId)
                .orElseThrow(() -> new IllegalStateException("Trip not found."));

        Instant now = Instant.now();
        jdbc.update("""
                UPDATE trips
                SET status = ?
                WHERE id = ?
                """, status.name(), tripId);

        AmbulanceStatus ambulanceStatus = switch (status) {
            case RESERVED -> AmbulanceStatus.RESERVED;
            case EN_ROUTE_PATIENT, EN_ROUTE_HOSPITAL -> AmbulanceStatus.ON_TRIP;
            case COMPLETED, CANCELLED -> AmbulanceStatus.AVAILABLE;
        };

        jdbc.update("""
                UPDATE ambulances
                SET status = ?, updated_at = ?
                WHERE id = ?
                """, ambulanceStatus.name(), Timestamp.from(now), trip.ambulanceId());

        if (status == TripStatus.COMPLETED) {
            jdbc.update("""
                    UPDATE incidents
                    SET status = ?
                    WHERE id = ?
                    """, IncidentStatus.COMPLETED.name(), trip.incidentId());
        } else if (status == TripStatus.CANCELLED) {
            jdbc.update("""
                    UPDATE incidents
                    SET status = ?
                    WHERE id = ?
                    """, IncidentStatus.CANCELLED.name(), trip.incidentId());
            jdbc.update("""
                    UPDATE hospitals
                    SET available_beds = LEAST(total_beds, available_beds + 1), updated_at = ?
                    WHERE id = ?
                    """, Timestamp.from(now), trip.hospitalId());
        }

        appendOutbox("Trip", tripId, "trip.status_changed", Map.of(
                "tripId", tripId,
                "status", status.name()
        ), now);

        return findTrip(tripId).orElseThrow();
    }

    @Override
    @Transactional
    public Hospital updateHospitalCapacity(String hospitalId, int availableBeds) {
        Hospital hospital = lockHospital(hospitalId)
                .orElseThrow(() -> new IllegalStateException("Hospital not found."));
        if (availableBeds < 0 || availableBeds > hospital.totalBeds()) {
            throw new IllegalStateException("Available beds must be between 0 and total beds.");
        }

        Instant now = Instant.now();
        jdbc.update("""
                UPDATE hospitals
                SET available_beds = ?, updated_at = ?
                WHERE id = ?
                """, availableBeds, Timestamp.from(now), hospitalId);

        appendOutbox("Hospital", hospitalId, "hospital.capacity_changed", Map.of(
                "hospitalId", hospitalId,
                "availableBeds", availableBeds
        ), now);

        return findHospital(hospitalId).orElseThrow();
    }

    @Override
    @Transactional
    public Trip rerouteTrip(
            String tripId,
            String hospitalId,
            CandidateScore winningScore,
            List<CandidateScore> alternatives
    ) {
        Trip trip = lockTrip(tripId)
                .orElseThrow(() -> new IllegalStateException("Trip not found."));
        Incident incident = lockIncident(trip.incidentId())
                .orElseThrow(() -> new IllegalStateException("Incident not found."));
        Ambulance ambulance = lockAmbulance(trip.ambulanceId())
                .orElseThrow(() -> new IllegalStateException("Ambulance not found."));
        Hospital hospital = lockHospital(hospitalId)
                .orElseThrow(() -> new IllegalStateException("Target hospital not found."));

        if (trip.status() == TripStatus.COMPLETED || trip.status() == TripStatus.CANCELLED) {
            throw new IllegalStateException("Completed or cancelled trips cannot be rerouted.");
        }
        if (trip.hospitalId().equals(hospitalId)) {
            throw new IllegalStateException("Trip is already assigned to this hospital.");
        }
        if (!ambulance.type().canHandle(requiredAmbulanceType(incident.condition(), incident.priority()))) {
            throw new IllegalStateException("Ambulance capability no longer satisfies the incident.");
        }
        if (!hospital.canTreat(incident.condition())) {
            throw new IllegalStateException("Target hospital cannot treat this condition.");
        }
        if (!hospital.hasCapacity()) {
            throw new IllegalStateException("Target hospital has no capacity.");
        }

        Instant now = Instant.now();
        String decisionId = newId("DEC");

        jdbc.update("""
                UPDATE hospitals
                SET available_beds = available_beds - 1, updated_at = ?
                WHERE id = ? AND available_beds > 0
                """, Timestamp.from(now), hospitalId);

        jdbc.update("""
                UPDATE trips
                SET hospital_id = ?, pickup_eta_minutes = ?, hospital_eta_minutes = ?, total_cost = ?
                WHERE id = ?
                """,
                hospitalId,
                winningScore.pickupEtaMinutes(),
                winningScore.hospitalEtaMinutes(),
                winningScore.totalCost(),
                tripId
        );

        jdbc.update("""
                INSERT INTO dispatch_decisions (id, incident_id, ambulance_id, hospital_id, pickup_eta_minutes,
                                                hospital_eta_minutes, hospital_load, quality_penalty,
                                                type_penalty, total_cost, explanation, created_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """,
                decisionId,
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
                Timestamp.from(now)
        );

        insertCandidateScores(decisionId, alternatives);

        appendOutbox("Trip", tripId, "trip.rerouted", Map.of(
                "tripId", tripId,
                "incidentId", trip.incidentId(),
                "ambulanceId", trip.ambulanceId(),
                "fromHospitalId", trip.hospitalId(),
                "toHospitalId", hospitalId,
                "decisionId", decisionId,
                "totalCost", winningScore.totalCost()
        ), now);

        return findTrip(tripId).orElseThrow();
    }

    @Override
    @Transactional
    public int publishPendingOutboxEvents(int limit) {
        List<String> eventIds = jdbc.query("""
                SELECT id
                FROM outbox_events
                WHERE published_at IS NULL
                ORDER BY created_at
                LIMIT ?
                FOR UPDATE SKIP LOCKED
                """,
                (rs, rowNum) -> rs.getString("id"),
                limit
        );

        Instant now = Instant.now();
        int published = 0;
        for (String eventId : eventIds) {
            published += jdbc.update("""
                    UPDATE outbox_events
                    SET published_at = ?
                    WHERE id = ? AND published_at IS NULL
                    """, Timestamp.from(now), eventId);
        }
        return published;
    }

    @Override
    @Transactional
    public void reset() {
        jdbc.execute("""
                TRUNCATE TABLE dispatch_candidate_scores, dispatch_decisions, outbox_events,
                               trips, incidents, hospital_specialties, hospitals, ambulances
                RESTART IDENTITY
                """);
        seedDemoData();
    }

    private Optional<Incident> lockIncident(String id) {
        return queryOptional("""
                SELECT id, patient_name, phone, condition, priority, latitude, longitude, created_at, status
                FROM incidents
                WHERE id = ?
                FOR UPDATE
                """, incidentMapper(), id);
    }

    private Optional<Ambulance> lockAmbulance(String id) {
        return queryOptional("""
                SELECT id, call_sign, type, status, latitude, longitude, base_station
                FROM ambulances
                WHERE id = ?
                FOR UPDATE
                """, ambulanceMapper(), id);
    }

    private Optional<Hospital> lockHospital(String id) {
        Map<String, Set<EmergencyCondition>> specialties = Map.of(id, hospitalSpecialties(id));
        return queryOptional("""
                SELECT id, name, latitude, longitude, total_beds, available_beds, quality_score
                FROM hospitals
                WHERE id = ?
                FOR UPDATE
                """, hospitalMapper(specialties), id);
    }

    private Optional<Trip> lockTrip(String id) {
        return queryOptional("""
                SELECT id, incident_id, ambulance_id, hospital_id, pickup_eta_minutes,
                       hospital_eta_minutes, total_cost, created_at, status
                FROM trips
                WHERE id = ?
                FOR UPDATE
                """, tripMapper(), id);
    }

    private void insertCandidateScores(String decisionId, List<CandidateScore> alternatives) {
        int rank = 1;
        for (CandidateScore score : alternatives) {
            jdbc.update("""
                    INSERT INTO dispatch_candidate_scores (dispatch_decision_id, rank, ambulance_id, hospital_id,
                                                           pickup_eta_minutes, hospital_eta_minutes, hospital_load,
                                                           quality_penalty, type_penalty, total_cost, explanation)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                    """,
                    decisionId,
                    rank,
                    score.ambulanceId(),
                    score.hospitalId(),
                    score.pickupEtaMinutes(),
                    score.hospitalEtaMinutes(),
                    score.hospitalLoad(),
                    score.qualityPenalty(),
                    score.typePenalty(),
                    score.totalCost(),
                    score.explanation()
            );
            rank += 1;
        }
    }

    private void seedDemoData() {
        insertAmbulance(new Ambulance("AMB-101", "Aster Alpha", AmbulanceType.ALS, AmbulanceStatus.AVAILABLE, new Location(12.9719, 77.6412), "Indiranagar"));
        insertAmbulance(new Ambulance("AMB-102", "Pulse Bravo", AmbulanceType.BLS, AmbulanceStatus.AVAILABLE, new Location(12.9352, 77.6245), "Koramangala"));
        insertAmbulance(new Ambulance("AMB-103", "Rescue Charlie", AmbulanceType.ICU, AmbulanceStatus.AVAILABLE, new Location(13.0118, 77.5549), "Malleshwaram"));
        insertAmbulance(new Ambulance("AMB-104", "Rapid Delta", AmbulanceType.ALS, AmbulanceStatus.AVAILABLE, new Location(12.9141, 77.6101), "BTM Layout"));
        insertAmbulance(new Ambulance("AMB-105", "Care Echo", AmbulanceType.BLS, AmbulanceStatus.AVAILABLE, new Location(12.9987, 77.5924), "Hebbal"));
        insertAmbulance(new Ambulance("AMB-106", "Life Foxtrot", AmbulanceType.ALS, AmbulanceStatus.OFFLINE, new Location(12.9698, 77.7500), "Whitefield"));

        insertHospital(new Hospital("HOS-201", "Narayana Cardiac Centre", new Location(12.9384, 77.6906), Set.of(EmergencyCondition.CARDIAC, EmergencyCondition.STROKE), 42, 7, 0.94));
        insertHospital(new Hospital("HOS-202", "Manipal Emergency Hospital", new Location(12.9592, 77.6489), Set.of(EmergencyCondition.TRAUMA, EmergencyCondition.CARDIAC, EmergencyCondition.GENERAL), 65, 14, 0.9));
        insertHospital(new Hospital("HOS-203", "Victoria Trauma Institute", new Location(12.9634, 77.5739), Set.of(EmergencyCondition.TRAUMA, EmergencyCondition.GENERAL), 58, 4, 0.86));
        insertHospital(new Hospital("HOS-204", "Cloudnine Pediatric ER", new Location(12.9336, 77.6234), Set.of(EmergencyCondition.PEDIATRIC, EmergencyCondition.GENERAL), 35, 9, 0.89));
        insertHospital(new Hospital("HOS-205", "Baptist North Care", new Location(13.0358, 77.5891), Set.of(EmergencyCondition.STROKE, EmergencyCondition.GENERAL), 44, 11, 0.84));

        Instant now = Instant.now();
        insertIncident(new Incident("INC-301", "Ananya Rao", "+91-90000-10001", EmergencyCondition.CARDIAC, IncidentPriority.CRITICAL, new Location(12.9458, 77.6309), now.minusSeconds(180), IncidentStatus.NEW));
        insertIncident(new Incident("INC-302", "Rohan Mehta", "+91-90000-10002", EmergencyCondition.TRAUMA, IncidentPriority.HIGH, new Location(12.9166, 77.6101), now.minusSeconds(90), IncidentStatus.NEW));
    }

    private void insertAmbulance(Ambulance ambulance) {
        jdbc.update("""
                INSERT INTO ambulances (id, call_sign, type, status, latitude, longitude, base_station)
                VALUES (?, ?, ?, ?, ?, ?, ?)
                """,
                ambulance.id(),
                ambulance.callSign(),
                ambulance.type().name(),
                ambulance.status().name(),
                ambulance.location().latitude(),
                ambulance.location().longitude(),
                ambulance.baseStation()
        );
    }

    private void insertHospital(Hospital hospital) {
        jdbc.update("""
                INSERT INTO hospitals (id, name, latitude, longitude, total_beds, available_beds, quality_score)
                VALUES (?, ?, ?, ?, ?, ?, ?)
                """,
                hospital.id(),
                hospital.name(),
                hospital.location().latitude(),
                hospital.location().longitude(),
                hospital.totalBeds(),
                hospital.availableBeds(),
                hospital.qualityScore()
        );
        for (EmergencyCondition specialty : hospital.specialties()) {
            jdbc.update("""
                    INSERT INTO hospital_specialties (hospital_id, condition)
                    VALUES (?, ?)
                    """, hospital.id(), specialty.name());
        }
    }

    private void insertIncident(Incident incident) {
        jdbc.update("""
                INSERT INTO incidents (id, patient_name, phone, condition, priority, latitude, longitude, created_at, status)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
                """,
                incident.id(),
                incident.patientName(),
                incident.phone(),
                incident.condition().name(),
                incident.priority().name(),
                incident.location().latitude(),
                incident.location().longitude(),
                Timestamp.from(incident.createdAt()),
                incident.status().name()
        );
    }

    private void appendOutbox(String aggregateType, String aggregateId, String eventType, Map<String, ?> payload, Instant now) {
        jdbc.update("""
                INSERT INTO outbox_events (id, aggregate_type, aggregate_id, event_type, payload, created_at)
                VALUES (?, ?, ?, ?, CAST(? AS jsonb), ?)
                """,
                newId("EVT"),
                aggregateType,
                aggregateId,
                eventType,
                toJson(payload),
                Timestamp.from(now)
        );
    }

    private Map<String, Set<EmergencyCondition>> allHospitalSpecialties() {
        return jdbc.query("""
                SELECT hospital_id, condition
                FROM hospital_specialties
                ORDER BY hospital_id, condition
                """, rs -> {
            Map<String, Set<EmergencyCondition>> result = new LinkedHashMap<>();
            while (rs.next()) {
                result.computeIfAbsent(rs.getString("hospital_id"), ignored -> new LinkedHashSet<>())
                        .add(EmergencyCondition.valueOf(rs.getString("condition")));
            }
            return result;
        });
    }

    private Set<EmergencyCondition> hospitalSpecialties(String hospitalId) {
        return new LinkedHashSet<>(jdbc.query("""
                SELECT condition
                FROM hospital_specialties
                WHERE hospital_id = ?
                ORDER BY condition
                """,
                (rs, rowNum) -> EmergencyCondition.valueOf(rs.getString("condition")),
                hospitalId
        ));
    }

    private RowMapper<Ambulance> ambulanceMapper() {
        return (rs, rowNum) -> new Ambulance(
                rs.getString("id"),
                rs.getString("call_sign"),
                AmbulanceType.valueOf(rs.getString("type")),
                AmbulanceStatus.valueOf(rs.getString("status")),
                new Location(rs.getDouble("latitude"), rs.getDouble("longitude")),
                rs.getString("base_station")
        );
    }

    private RowMapper<Hospital> hospitalMapper(Map<String, Set<EmergencyCondition>> specialties) {
        return (rs, rowNum) -> new Hospital(
                rs.getString("id"),
                rs.getString("name"),
                new Location(rs.getDouble("latitude"), rs.getDouble("longitude")),
                specialties.getOrDefault(rs.getString("id"), Set.of()),
                rs.getInt("total_beds"),
                rs.getInt("available_beds"),
                rs.getDouble("quality_score")
        );
    }

    private RowMapper<Incident> incidentMapper() {
        return (rs, rowNum) -> new Incident(
                rs.getString("id"),
                rs.getString("patient_name"),
                rs.getString("phone"),
                EmergencyCondition.valueOf(rs.getString("condition")),
                IncidentPriority.valueOf(rs.getString("priority")),
                new Location(rs.getDouble("latitude"), rs.getDouble("longitude")),
                instant(rs, "created_at"),
                IncidentStatus.valueOf(rs.getString("status"))
        );
    }

    private RowMapper<Trip> tripMapper() {
        return (rs, rowNum) -> new Trip(
                rs.getString("id"),
                rs.getString("incident_id"),
                rs.getString("ambulance_id"),
                rs.getString("hospital_id"),
                rs.getDouble("pickup_eta_minutes"),
                rs.getDouble("hospital_eta_minutes"),
                rs.getDouble("total_cost"),
                instant(rs, "created_at"),
                TripStatus.valueOf(rs.getString("status"))
        );
    }

    private RowMapper<DispatchAuditRecord> dispatchAuditMapper() {
        return (rs, rowNum) -> new DispatchAuditRecord(
                rs.getString("id"),
                rs.getString("incident_id"),
                rs.getString("ambulance_id"),
                rs.getString("hospital_id"),
                rs.getDouble("pickup_eta_minutes"),
                rs.getDouble("hospital_eta_minutes"),
                rs.getDouble("hospital_load"),
                rs.getDouble("quality_penalty"),
                rs.getDouble("type_penalty"),
                rs.getDouble("total_cost"),
                rs.getString("explanation"),
                instant(rs, "created_at")
        );
    }

    private RowMapper<OutboxEvent> outboxEventMapper() {
        return (rs, rowNum) -> new OutboxEvent(
                rs.getString("id"),
                rs.getString("aggregate_type"),
                rs.getString("aggregate_id"),
                rs.getString("event_type"),
                rs.getString("payload"),
                instant(rs, "created_at"),
                instant(rs, "published_at")
        );
    }

    private <T> Optional<T> queryOptional(String sql, RowMapper<T> rowMapper, Object... args) {
        List<T> rows = jdbc.query(sql, rowMapper, args);
        if (rows.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(rows.getFirst());
    }

    private Instant instant(ResultSet rs, String column) throws SQLException {
        OffsetDateTime value = rs.getObject(column, OffsetDateTime.class);
        return value == null ? null : value.toInstant();
    }

    private String newId(String prefix) {
        return prefix + "-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
    }

    private String toJson(Map<String, ?> payload) {
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Unable to serialize outbox payload.", exception);
        }
    }

    private AmbulanceType requiredAmbulanceType(EmergencyCondition condition, IncidentPriority priority) {
        if (priority == IncidentPriority.CRITICAL) {
            return AmbulanceType.ALS;
        }
        return switch (condition) {
            case CARDIAC, STROKE -> AmbulanceType.ALS;
            case TRAUMA -> priority == IncidentPriority.HIGH ? AmbulanceType.ALS : AmbulanceType.BLS;
            case PEDIATRIC, GENERAL -> AmbulanceType.BLS;
        };
    }
}
