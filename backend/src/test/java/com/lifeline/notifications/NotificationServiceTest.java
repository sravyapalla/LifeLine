package com.lifeline.notifications;

import com.lifeline.domain.EmergencyCondition;
import com.lifeline.domain.IncidentPriority;
import com.lifeline.domain.Location;
import com.lifeline.domain.NotificationRole;
import com.lifeline.outbox.OutboxProcessor;
import com.lifeline.store.InMemoryLifeLineStore;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class NotificationServiceTest {
    @Test
    void createsRoleNotificationsAfterOutboxPublishSucceeds() {
        InMemoryLifeLineStore store = new InMemoryLifeLineStore();
        store.reset();
        store.createIncident(
                "Patient",
                "+91-90000-00000",
                EmergencyCondition.CARDIAC,
                IncidentPriority.CRITICAL,
                new Location(12.95, 77.63)
        );
        NotificationService notificationService = new NotificationService(store);
        OutboxProcessor processor = new OutboxProcessor(store, event -> {
        }, notificationService, false, 25, 30);

        processor.publishPendingNow();

        assertThat(store.notifications(NotificationRole.PATIENT))
                .extracting(notification -> notification.title())
                .contains("Request received");
        assertThat(store.notifications(NotificationRole.CONTROL))
                .extracting(notification -> notification.title())
                .contains("New incident queued");
        assertThat(store.notificationBacklog()).isEqualTo(2);
    }
}
