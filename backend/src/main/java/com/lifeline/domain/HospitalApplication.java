package com.lifeline.domain;

import java.time.Instant;
import java.util.Set;

public record HospitalApplication(
        String id,
        String hospitalName,
        String contactName,
        String contactPhone,
        String addressText,
        Location location,
        Set<EmergencyCondition> specialties,
        int totalBeds,
        HospitalApplicationStatus status,
        Instant createdAt,
        Instant reviewedAt
) {
}
