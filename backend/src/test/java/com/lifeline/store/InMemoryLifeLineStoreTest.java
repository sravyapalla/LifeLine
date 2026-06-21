package com.lifeline.store;

import com.lifeline.domain.EmergencyCondition;
import com.lifeline.domain.IncidentPriority;
import com.lifeline.domain.Location;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class InMemoryLifeLineStoreTest {
    @Test
    void publishesPendingOutboxEvents() {
        InMemoryLifeLineStore store = new InMemoryLifeLineStore();
        store.reset();

        store.createIncident(
                "Patient",
                "+91-90000-00000",
                EmergencyCondition.CARDIAC,
                IncidentPriority.CRITICAL,
                new Location(12.95, 77.63)
        );

        assertThat(store.pendingOutboxEventCount()).isEqualTo(1);
        assertThat(store.pendingOutboxEvents(10)).hasSize(1);

        int published = store.publishPendingOutboxEvents(10);

        assertThat(published).isEqualTo(1);
        assertThat(store.pendingOutboxEventCount()).isZero();
        assertThat(store.outboxEvents()).allSatisfy(event -> assertThat(event.publishedAt()).isNotNull());
    }
}
