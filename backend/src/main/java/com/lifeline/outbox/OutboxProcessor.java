package com.lifeline.outbox;

import com.lifeline.domain.OutboxEvent;
import com.lifeline.notifications.NotificationService;
import com.lifeline.store.LifeLineStore;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

@Service
public class OutboxProcessor {
    private final LifeLineStore store;
    private final OutboxPublisher publisher;
    private final NotificationService notificationService;
    private final boolean automaticPublishingEnabled;
    private final int batchSize;
    private final Duration retryDelay;

    @Autowired
    public OutboxProcessor(
            LifeLineStore store,
            OutboxPublisher publisher,
            NotificationService notificationService,
            @Value("${lifeline.outbox.publish.enabled:false}") boolean automaticPublishingEnabled,
            @Value("${lifeline.outbox.publish.batch-size:25}") int batchSize,
            @Value("${lifeline.outbox.publish.retry-delay-seconds:30}") long retryDelaySeconds
    ) {
        this.store = store;
        this.publisher = publisher;
        this.notificationService = notificationService;
        this.automaticPublishingEnabled = automaticPublishingEnabled;
        this.batchSize = batchSize;
        this.retryDelay = Duration.ofSeconds(retryDelaySeconds);
    }

    public OutboxProcessor(
            LifeLineStore store,
            OutboxPublisher publisher,
            boolean automaticPublishingEnabled,
            int batchSize,
            long retryDelaySeconds
    ) {
        this(store, publisher, null, automaticPublishingEnabled, batchSize, retryDelaySeconds);
    }

    public OutboxPublishResult publishPendingNow() {
        Instant processedAt = Instant.now();
        List<OutboxEvent> events = store.claimReadyOutboxEvents(batchSize, processedAt, processedAt.plus(retryDelay));
        int published = 0;
        int failed = 0;

        for (OutboxEvent event : events) {
            try {
                publisher.publish(event);
                store.markOutboxEventPublished(event.id(), processedAt);
                if (notificationService != null) {
                    notificationService.createForPublishedEvent(event, processedAt);
                }
                published += 1;
            } catch (RuntimeException exception) {
                store.markOutboxEventFailed(event.id(), safeFailureReason(exception));
                failed += 1;
            }
        }

        return new OutboxPublishResult(published, failed, store.pendingOutboxEventCount(), processedAt);
    }

    @Scheduled(fixedDelayString = "${lifeline.outbox.publish.fixed-delay-ms:5000}")
    public void publishPendingOnSchedule() {
        if (automaticPublishingEnabled) {
            publishPendingNow();
        }
    }

    private String safeFailureReason(RuntimeException exception) {
        String message = exception.getMessage();
        if (message == null || message.isBlank()) {
            return exception.getClass().getSimpleName();
        }
        return message.length() <= 300 ? message : message.substring(0, 300);
    }
}
