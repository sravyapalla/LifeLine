package com.lifeline.outbox;

import com.lifeline.domain.OutboxEvent;

@FunctionalInterface
public interface OutboxPublisher {
    void publish(OutboxEvent event);
}
