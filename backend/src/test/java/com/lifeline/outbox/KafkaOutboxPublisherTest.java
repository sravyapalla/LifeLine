package com.lifeline.outbox;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lifeline.domain.OutboxEvent;
import org.junit.jupiter.api.Test;
import org.springframework.kafka.core.KafkaTemplate;

import java.time.Instant;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class KafkaOutboxPublisherTest {
    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

    @Test
    void publishesEnvelopeJsonToConfiguredTopic() {
        KafkaTemplate<String, String> kafkaTemplate = mock(KafkaTemplate.class);
        when(kafkaTemplate.send(anyString(), anyString(), anyString())).thenReturn(CompletableFuture.completedFuture(null));
        KafkaOutboxPublisher publisher = new KafkaOutboxPublisher(kafkaTemplate, objectMapper, "lifeline.outbox.events", 1);

        assertThatCode(() -> publisher.publish(event()))
                .doesNotThrowAnyException();
    }

    @Test
    void surfacesKafkaFailureToOutboxProcessorRetryFlow() {
        KafkaTemplate<String, String> kafkaTemplate = mock(KafkaTemplate.class);
        when(kafkaTemplate.send(anyString(), anyString(), anyString()))
                .thenReturn(CompletableFuture.failedFuture(new IllegalStateException("broker unavailable")));
        KafkaOutboxPublisher publisher = new KafkaOutboxPublisher(kafkaTemplate, objectMapper, "lifeline.outbox.events", 1);

        assertThatThrownBy(() -> publisher.publish(event()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Kafka publish failed")
                .hasMessageContaining("broker unavailable");
    }

    private OutboxEvent event() {
        return new OutboxEvent(
                "EVT-1",
                "Dispatch",
                "TRIP-1",
                "dispatch.reserved",
                "{\"tripId\":\"TRIP-1\"}",
                Instant.parse("2026-06-21T08:00:00Z"),
                null,
                1,
                Instant.parse("2026-06-21T08:01:00Z"),
                null,
                Instant.parse("2026-06-21T08:02:00Z")
        );
    }
}
