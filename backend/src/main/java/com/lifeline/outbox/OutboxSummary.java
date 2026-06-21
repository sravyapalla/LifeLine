package com.lifeline.outbox;

import java.time.Instant;
import java.util.List;

public record OutboxSummary(
        int totalEvents,
        int pendingEvents,
        int publishedEvents,
        int readyEvents,
        int failedEvents,
        int retryScheduledEvents,
        long oldestPendingAgeSeconds,
        Instant oldestPendingAt,
        Instant lastPublishedAt,
        List<OutboxEventTypeSummary> eventTypes
) {
}
