package com.lifeline.domain;

import java.time.Instant;

public record Incident(
        String id,
        String requesterUserId,
        String patientName,
        String phone,
        EmergencyCondition condition,
        IncidentPriority priority,
        Location location,
        Instant createdAt,
        IncidentStatus status
) {
    public Incident withStatus(IncidentStatus nextStatus) {
        return new Incident(id, requesterUserId, patientName, phone, condition, priority, location, createdAt, nextStatus);
    }
}
