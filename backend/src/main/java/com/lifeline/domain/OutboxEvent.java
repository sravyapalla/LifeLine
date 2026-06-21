package com.lifeline.domain;

import java.time.Instant;

public record OutboxEvent(
        String id,
        String aggregateType,
        String aggregateId,
        String eventType,
        String payload,
        Instant createdAt,
        Instant publishedAt,
        int publishAttempts,
        Instant lastPublishAttemptAt,
        String lastPublishError,
        Instant nextPublishAttemptAt
) {
    public OutboxEvent publishedAt(Instant publishedAt) {
        return new OutboxEvent(
                id,
                aggregateType,
                aggregateId,
                eventType,
                payload,
                createdAt,
                publishedAt,
                publishAttempts,
                lastPublishAttemptAt,
                null,
                null
        );
    }

    public OutboxEvent claimedAt(Instant attemptedAt, Instant nextAttemptAt) {
        return new OutboxEvent(
                id,
                aggregateType,
                aggregateId,
                eventType,
                payload,
                createdAt,
                publishedAt,
                publishAttempts + 1,
                attemptedAt,
                lastPublishError,
                nextAttemptAt
        );
    }

    public OutboxEvent failedWith(String failureReason) {
        return new OutboxEvent(
                id,
                aggregateType,
                aggregateId,
                eventType,
                payload,
                createdAt,
                publishedAt,
                publishAttempts,
                lastPublishAttemptAt,
                failureReason,
                nextPublishAttemptAt
        );
    }

    public boolean isPublished() {
        return publishedAt != null;
    }

    public boolean isReadyForPublish(Instant now) {
        return !isPublished() && (nextPublishAttemptAt == null || !nextPublishAttemptAt.isAfter(now));
    }
}
