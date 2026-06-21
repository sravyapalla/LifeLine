package com.lifeline.outbox;

public record OutboxEventTypeSummary(
        String eventType,
        int total,
        int pending,
        int published
) {
}
