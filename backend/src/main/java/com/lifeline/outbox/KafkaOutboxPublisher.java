package com.lifeline.outbox;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lifeline.domain.OutboxEvent;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

@Component
@ConditionalOnProperty(name = "lifeline.outbox.publisher.mode", havingValue = "kafka")
public class KafkaOutboxPublisher implements OutboxPublisher {
    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper;
    private final String topic;
    private final long timeoutSeconds;

    public KafkaOutboxPublisher(
            KafkaTemplate<String, String> kafkaTemplate,
            ObjectMapper objectMapper,
            @Value("${lifeline.kafka.outbox-topic:lifeline.outbox.events}") String topic,
            @Value("${lifeline.kafka.publish-timeout-seconds:5}") long timeoutSeconds
    ) {
        this.kafkaTemplate = kafkaTemplate;
        this.objectMapper = objectMapper;
        this.topic = topic;
        this.timeoutSeconds = timeoutSeconds;
    }

    @Override
    public void publish(OutboxEvent event) {
        try {
            JsonNode payload = objectMapper.readTree(event.payload());
            OutboxEventEnvelope envelope = OutboxEventEnvelope.from(event, payload);
            String value = objectMapper.writeValueAsString(envelope);
            kafkaTemplate.send(topic, event.aggregateId(), value).get(timeoutSeconds, TimeUnit.SECONDS);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Kafka publish interrupted.", exception);
        } catch (JsonProcessingException | ExecutionException | TimeoutException exception) {
            throw new IllegalStateException("Kafka publish failed: " + rootMessage(exception), exception);
        }
    }

    private String rootMessage(Exception exception) {
        Throwable cause = exception.getCause();
        if (cause != null && cause.getMessage() != null && !cause.getMessage().isBlank()) {
            return cause.getMessage();
        }
        return exception.getMessage() == null ? exception.getClass().getSimpleName() : exception.getMessage();
    }
}
