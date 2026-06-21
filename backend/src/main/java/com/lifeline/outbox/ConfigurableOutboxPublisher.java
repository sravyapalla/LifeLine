package com.lifeline.outbox;

import com.lifeline.domain.OutboxEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class ConfigurableOutboxPublisher implements OutboxPublisher {
    private static final Logger LOGGER = LoggerFactory.getLogger(ConfigurableOutboxPublisher.class);

    private final String mode;
    private final String failEventType;
    private final String failureMessage;

    public ConfigurableOutboxPublisher(
            @Value("${lifeline.outbox.publisher.mode:logging}") String mode,
            @Value("${lifeline.outbox.publisher.fail-event-type:}") String failEventType,
            @Value("${lifeline.outbox.publisher.failure-message:Configured outbox publisher failure}") String failureMessage
    ) {
        this.mode = mode;
        this.failEventType = failEventType;
        this.failureMessage = failureMessage;
    }

    @Override
    public void publish(OutboxEvent event) {
        if (shouldFail(event)) {
            throw new IllegalStateException(failureMessage);
        }
        LOGGER.info("Published outbox event {} of type {}", event.id(), event.eventType());
    }

    private boolean shouldFail(OutboxEvent event) {
        return switch (mode) {
            case "fail-all" -> true;
            case "fail-event-type" -> event.eventType().equals(failEventType);
            case "logging" -> false;
            default -> throw new IllegalStateException("Unsupported outbox publisher mode: " + mode);
        };
    }
}
