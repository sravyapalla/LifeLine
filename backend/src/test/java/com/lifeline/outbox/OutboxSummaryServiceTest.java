package com.lifeline.outbox;

import com.lifeline.domain.OutboxEvent;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class OutboxSummaryServiceTest {
    private final OutboxSummaryService service = new OutboxSummaryService();

    @Test
    void summarizesPendingPublishedAndEventTypes() {
        Instant now = Instant.parse("2026-06-21T08:00:00Z");
        List<OutboxEvent> events = List.of(
                event("EVT-1", "Incident", "INC-1", "incident.created", now.minusSeconds(90), null, 1, now.minusSeconds(10), "adapter down", now.plusSeconds(20)),
                event("EVT-2", "Trip", "TRIP-1", "dispatch.reserved", now.minusSeconds(60), now.minusSeconds(30), 1, now.minusSeconds(30), null, null),
                event("EVT-3", "Trip", "TRIP-1", "dispatch.reserved", now.minusSeconds(30), null, 0, null, null, null)
        );

        OutboxSummary summary = service.summarize(events, now);

        assertThat(summary.totalEvents()).isEqualTo(3);
        assertThat(summary.pendingEvents()).isEqualTo(2);
        assertThat(summary.publishedEvents()).isEqualTo(1);
        assertThat(summary.readyEvents()).isEqualTo(1);
        assertThat(summary.failedEvents()).isEqualTo(1);
        assertThat(summary.retryScheduledEvents()).isEqualTo(1);
        assertThat(summary.oldestPendingAgeSeconds()).isEqualTo(90);
        assertThat(summary.oldestPendingAt()).isEqualTo(now.minusSeconds(90));
        assertThat(summary.lastPublishedAt()).isEqualTo(now.minusSeconds(30));
        assertThat(summary.eventTypes()).containsExactly(
                new OutboxEventTypeSummary("dispatch.reserved", 2, 1, 0, 1),
                new OutboxEventTypeSummary("incident.created", 1, 1, 1, 0)
        );
    }

    private OutboxEvent event(
            String id,
            String aggregateType,
            String aggregateId,
            String eventType,
            Instant createdAt,
            Instant publishedAt,
            int publishAttempts,
            Instant lastPublishAttemptAt,
            String lastPublishError,
            Instant nextPublishAttemptAt
    ) {
        return new OutboxEvent(
                id,
                aggregateType,
                aggregateId,
                eventType,
                "{}",
                createdAt,
                publishedAt,
                publishAttempts,
                lastPublishAttemptAt,
                lastPublishError,
                nextPublishAttemptAt
        );
    }
}
