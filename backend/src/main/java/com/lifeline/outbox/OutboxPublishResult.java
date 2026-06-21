package com.lifeline.outbox;

import java.time.Instant;

public record OutboxPublishResult(
        int published,
        int pending,
        Instant processedAt
) {
}
