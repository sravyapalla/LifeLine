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
                new OutboxEvent("EVT-1", "Incident", "INC-1", "incident.created", "{}", now.minusSeconds(90), null),
                new OutboxEvent("EVT-2", "Trip", "TRIP-1", "dispatch.reserved", "{}", now.minusSeconds(60), now.minusSeconds(30)),
                new OutboxEvent("EVT-3", "Trip", "TRIP-1", "dispatch.reserved", "{}", now.minusSeconds(30), null)
        );

        OutboxSummary summary = service.summarize(events, now);

        assertThat(summary.totalEvents()).isEqualTo(3);
        assertThat(summary.pendingEvents()).isEqualTo(2);
        assertThat(summary.publishedEvents()).isEqualTo(1);
        assertThat(summary.oldestPendingAgeSeconds()).isEqualTo(90);
        assertThat(summary.oldestPendingAt()).isEqualTo(now.minusSeconds(90));
        assertThat(summary.lastPublishedAt()).isEqualTo(now.minusSeconds(30));
        assertThat(summary.eventTypes()).containsExactly(
                new OutboxEventTypeSummary("dispatch.reserved", 2, 1, 1),
                new OutboxEventTypeSummary("incident.created", 1, 1, 0)
        );
    }
}
