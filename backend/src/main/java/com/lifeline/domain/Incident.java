package com.lifeline.domain;

import java.time.Instant;

public record Incident(
        String id,
        String patientName,
        String phone,
        EmergencyCondition condition,
        IncidentPriority priority,
        Location location,
        Instant createdAt,
        IncidentStatus status
) {
    public Incident withStatus(IncidentStatus nextStatus) {
        return new Incident(id, patientName, phone, condition, priority, location, createdAt, nextStatus);
    }
}

