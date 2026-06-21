package com.lifeline.outbox;

import java.time.Instant;

public record OutboxPublishResult(
        int published,
        int failed,
        int pending,
        Instant processedAt
) {
}
