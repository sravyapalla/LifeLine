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
        String addressText,
        String landmark,
        String locationSource,
        Instant createdAt,
        IncidentStatus status
) {
    public Incident withStatus(IncidentStatus nextStatus) {
        return new Incident(id, requesterUserId, patientName, phone, condition, priority, location, addressText, landmark, locationSource, createdAt, nextStatus);
    }
}
