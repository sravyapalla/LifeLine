package com.lifeline.domain;

import java.time.Instant;

public record SecurityAuditEvent(
        String id,
        String actorUserId,
        String actorRole,
        String action,
        String resourceType,
        String resourceId,
        String outcome,
        String reason,
        String metadata,
        Instant createdAt
) {
}
