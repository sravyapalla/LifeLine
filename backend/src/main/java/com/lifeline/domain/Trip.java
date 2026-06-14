package com.lifeline.domain;

import java.time.Instant;

public record Trip(
        String id,
        String incidentId,
        String ambulanceId,
        String hospitalId,
        double pickupEtaMinutes,
        double hospitalEtaMinutes,
        double totalCost,
        Instant createdAt,
        TripStatus status
) {
}

