package com.lifeline.outbox;

import java.time.Instant;
import java.util.List;

public record OutboxSummary(
        int totalEvents,
        int pendingEvents,
        int publishedEvents,
        long oldestPendingAgeSeconds,
        Instant oldestPendingAt,
        Instant lastPublishedAt,
        List<OutboxEventTypeSummary> eventTypes
) {
}
