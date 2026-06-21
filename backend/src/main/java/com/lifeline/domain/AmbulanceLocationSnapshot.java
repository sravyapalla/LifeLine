package com.lifeline.domain;

import java.time.Instant;

public record AmbulanceLocationSnapshot(
        String ambulanceId,
        Location location,
        Instant updatedAt,
        Instant expiresAt,
        String source
) {
    public boolean isFresh(Instant now) {
        return expiresAt == null || expiresAt.isAfter(now);
    }
}
