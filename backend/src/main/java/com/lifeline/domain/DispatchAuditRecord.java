package com.lifeline.domain;

import java.time.Instant;

public record DispatchAuditRecord(
        String id,
        String incidentId,
        String ambulanceId,
        String hospitalId,
        double pickupEtaMinutes,
        double hospitalEtaMinutes,
        double hospitalLoad,
        double qualityPenalty,
        double typePenalty,
        double totalCost,
        String explanation,
        Instant createdAt
) {
}

