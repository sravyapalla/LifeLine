package com.lifeline.security;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lifeline.domain.SecurityAuditEvent;
import com.lifeline.store.LifeLineStore;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class SecurityAuditService {
    private static final int MAX_LIMIT = 500;

    private final LifeLineStore store;
    private final ObjectMapper objectMapper;

    public SecurityAuditService(LifeLineStore store, ObjectMapper objectMapper) {
        this.store = store;
        this.objectMapper = objectMapper;
    }

    public List<SecurityAuditEvent> events(int limit) {
        int safeLimit = Math.max(1, Math.min(limit, MAX_LIMIT));
        return store.securityAuditEvents(safeLimit);
    }

    public SecurityAuditEvent allowed(
            AuthenticatedUser actor,
            String action,
            String resourceType,
            String resourceId,
            String reason,
            Map<String, ?> metadata
    ) {
        return record(actor.username(), actor.role().name(), action, resourceType, resourceId, "ALLOWED", reason, metadata);
    }

    public SecurityAuditEvent denied(
            AuthenticatedUser actor,
            String action,
            String resourceType,
            String resourceId,
            String reason,
            Map<String, ?> metadata
    ) {
        return record(actor.username(), actor.role().name(), action, resourceType, resourceId, "DENIED", reason, metadata);
    }

    public SecurityAuditEvent anonymous(
            String actorUserId,
            String action,
            String resourceType,
            String resourceId,
            String outcome,
            String reason,
            Map<String, ?> metadata
    ) {
        return record(actorUserId, "ANONYMOUS", action, resourceType, resourceId, outcome, reason, metadata);
    }

    private SecurityAuditEvent record(
            String actorUserId,
            String actorRole,
            String action,
            String resourceType,
            String resourceId,
            String outcome,
            String reason,
            Map<String, ?> metadata
    ) {
        SecurityAuditEvent event = new SecurityAuditEvent(
                "AUD-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase(),
                actorUserId,
                actorRole,
                action,
                resourceType,
                resourceId,
                outcome,
                reason,
                toJson(metadata == null ? Map.of() : metadata),
                Instant.now()
        );
        return store.addSecurityAuditEvent(event);
    }

    private String toJson(Map<String, ?> metadata) {
        try {
            return objectMapper.writeValueAsString(metadata);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Unable to serialize audit metadata.", exception);
        }
    }
}
