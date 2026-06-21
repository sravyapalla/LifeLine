package com.lifeline.outbox;

import com.lifeline.domain.OutboxEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class LoggingOutboxPublisher implements OutboxPublisher {
    private static final Logger LOGGER = LoggerFactory.getLogger(LoggingOutboxPublisher.class);

    @Override
    public void publish(OutboxEvent event) {
        LOGGER.info("Published outbox event {} of type {}", event.id(), event.eventType());
    }
}
