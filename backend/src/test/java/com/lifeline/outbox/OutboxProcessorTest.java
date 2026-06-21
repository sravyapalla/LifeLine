package com.lifeline.outbox;

import com.lifeline.domain.EmergencyCondition;
import com.lifeline.domain.IncidentPriority;
import com.lifeline.domain.Location;
import com.lifeline.domain.OutboxEvent;
import com.lifeline.store.InMemoryLifeLineStore;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class OutboxProcessorTest {
    @Test
    void publishesClaimedEventsAndClearsRetryState() {
        InMemoryLifeLineStore store = storeWithIncidentEvent();
        OutboxProcessor processor = new OutboxProcessor(store, event -> {
        }, false, 25, 30);

        OutboxPublishResult result = processor.publishPendingNow();

        assertThat(result.published()).isEqualTo(1);
        assertThat(result.failed()).isZero();
        assertThat(result.pending()).isZero();

        OutboxEvent event = store.outboxEvents().getFirst();
        assertThat(event.publishedAt()).isNotNull();
        assertThat(event.publishAttempts()).isEqualTo(1);
        assertThat(event.lastPublishAttemptAt()).isNotNull();
        assertThat(event.lastPublishError()).isNull();
        assertThat(event.nextPublishAttemptAt()).isNull();
    }

    @Test
    void recordsFailureAndSchedulesRetry() {
        InMemoryLifeLineStore store = storeWithIncidentEvent();
        OutboxProcessor processor = new OutboxProcessor(store, event -> {
            throw new IllegalStateException("adapter unavailable");
        }, false, 25, 30);

        OutboxPublishResult result = processor.publishPendingNow();

        assertThat(result.published()).isZero();
        assertThat(result.failed()).isEqualTo(1);
        assertThat(result.pending()).isEqualTo(1);

        OutboxEvent event = store.outboxEvents().getFirst();
        assertThat(event.publishedAt()).isNull();
        assertThat(event.publishAttempts()).isEqualTo(1);
        assertThat(event.lastPublishAttemptAt()).isNotNull();
        assertThat(event.lastPublishError()).isEqualTo("adapter unavailable");
        assertThat(event.nextPublishAttemptAt()).isAfter(event.lastPublishAttemptAt());
        assertThat(store.claimReadyOutboxEvents(10, Instant.now(), Instant.now())).isEmpty();
    }

    private InMemoryLifeLineStore storeWithIncidentEvent() {
        InMemoryLifeLineStore store = new InMemoryLifeLineStore();
        store.reset();
        store.createIncident(
                "Patient",
                "+91-90000-00000",
                EmergencyCondition.CARDIAC,
                IncidentPriority.CRITICAL,
                new Location(12.95, 77.63)
        );
        return store;
    }
}
