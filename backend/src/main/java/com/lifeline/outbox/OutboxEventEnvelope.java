package com.lifeline.outbox;

import com.fasterxml.jackson.databind.JsonNode;
import com.lifeline.domain.OutboxEvent;

import java.time.Instant;

public record OutboxEventEnvelope(
        String eventId,
        String aggregateType,
        String aggregateId,
        String eventType,
        JsonNode payload,
        Instant createdAt,
        int publishAttempts,
        Instant lastPublishAttemptAt
) {
    public static OutboxEventEnvelope from(OutboxEvent event, JsonNode payload) {
        return new OutboxEventEnvelope(
                event.id(),
                event.aggregateType(),
                event.aggregateId(),
                event.eventType(),
                payload,
                event.createdAt(),
                event.publishAttempts(),
                event.lastPublishAttemptAt()
        );
    }
}
