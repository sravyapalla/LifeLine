package com.lifeline.outbox;

import com.lifeline.domain.OutboxEvent;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ConfigurableOutboxPublisherTest {
    @Test
    void loggingModePublishesWithoutFailure() {
        ConfigurableOutboxPublisher publisher = new ConfigurableOutboxPublisher("logging", "", "boom");

        assertThatCode(() -> publisher.publish(event("incident.created"))).doesNotThrowAnyException();
    }

    @Test
    void failAllModeThrowsConfiguredFailure() {
        ConfigurableOutboxPublisher publisher = new ConfigurableOutboxPublisher("fail-all", "", "adapter down");

        assertThatThrownBy(() -> publisher.publish(event("incident.created")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("adapter down");
    }

    @Test
    void failEventTypeModeOnlyFailsMatchingEventType() {
        ConfigurableOutboxPublisher publisher = new ConfigurableOutboxPublisher("fail-event-type", "trip.rerouted", "adapter down");

        assertThatCode(() -> publisher.publish(event("incident.created"))).doesNotThrowAnyException();
        assertThatThrownBy(() -> publisher.publish(event("trip.rerouted")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("adapter down");
    }

    private OutboxEvent event(String eventType) {
        return new OutboxEvent(
                "EVT-1",
                "Incident",
                "INC-1",
                eventType,
                "{}",
                Instant.parse("2026-06-21T08:00:00Z"),
                null,
                0,
                null,
                null,
                null
        );
    }
}
