package com.lifeline.domain;

import java.time.Instant;

public record OutboxEvent(
        String id,
        String aggregateType,
        String aggregateId,
        String eventType,
        String payload,
        Instant createdAt,
        Instant publishedAt
) {
    public OutboxEvent publishedAt(Instant publishedAt) {
        return new OutboxEvent(id, aggregateType, aggregateId, eventType, payload, createdAt, publishedAt);
    }
}
