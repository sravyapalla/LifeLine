package com.lifeline.outbox;

import com.lifeline.store.LifeLineStore;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Instant;

@Service
public class OutboxProcessor {
    private final LifeLineStore store;
    private final boolean automaticPublishingEnabled;
    private final int batchSize;

    public OutboxProcessor(
            LifeLineStore store,
            @Value("${lifeline.outbox.publish.enabled:false}") boolean automaticPublishingEnabled,
            @Value("${lifeline.outbox.publish.batch-size:25}") int batchSize
    ) {
        this.store = store;
        this.automaticPublishingEnabled = automaticPublishingEnabled;
        this.batchSize = batchSize;
    }

    public OutboxPublishResult publishPendingNow() {
        int published = store.publishPendingOutboxEvents(batchSize);
        return new OutboxPublishResult(published, store.pendingOutboxEventCount(), Instant.now());
    }

    @Scheduled(fixedDelayString = "${lifeline.outbox.publish.fixed-delay-ms:5000}")
    public void publishPendingOnSchedule() {
        if (automaticPublishingEnabled) {
            store.publishPendingOutboxEvents(batchSize);
        }
    }
}
