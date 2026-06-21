package com.lifeline.store;

import com.lifeline.domain.EmergencyCondition;
import com.lifeline.domain.IncidentPriority;
import com.lifeline.domain.Location;
import com.lifeline.domain.OutboxEvent;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE, properties = {
        "lifeline.outbox.publish.enabled=false"
})
@EnabledIfSystemProperty(named = "lifeline.integration.postgres", matches = "true")
class JdbcOutboxIntegrationTest {
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>(
            DockerImageName.parse("postgis/postgis:16-3.4").asCompatibleSubstituteFor("postgres")
    )
            .withDatabaseName("lifeline")
            .withUsername("lifeline")
            .withPassword("lifeline");

    static {
        if ("true".equals(System.getProperty("lifeline.integration.postgres"))) {
            POSTGRES.start();
        }
    }

    @DynamicPropertySource
    static void postgresProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @AfterAll
    static void stopPostgres() {
        if (POSTGRES.isRunning()) {
            POSTGRES.stop();
        }
    }

    @Autowired
    LifeLineStore store;

    @Test
    void claimsRetryReadyEventsAndPersistsSuccessAndFailureState() {
        store.reset();
        store.createIncident(
                "Postgres Patient",
                "+91-90000-54321",
                EmergencyCondition.CARDIAC,
                IncidentPriority.CRITICAL,
                new Location(12.95, 77.63)
        );

        Instant firstAttemptAt = Instant.parse("2026-06-21T08:00:00Z");
        Instant firstRetryAt = firstAttemptAt.plusSeconds(30);
        List<OutboxEvent> firstClaim = store.claimReadyOutboxEvents(10, firstAttemptAt, firstRetryAt);

        assertThat(firstClaim).hasSize(1);
        OutboxEvent claimed = firstClaim.getFirst();
        assertThat(claimed.publishAttempts()).isEqualTo(1);
        assertThat(claimed.lastPublishAttemptAt()).isEqualTo(firstAttemptAt);
        assertThat(claimed.nextPublishAttemptAt()).isEqualTo(firstRetryAt);

        store.markOutboxEventFailed(claimed.id(), "adapter down");
        OutboxEvent failed = store.outboxEvents().getFirst();
        assertThat(failed.publishedAt()).isNull();
        assertThat(failed.lastPublishError()).isEqualTo("adapter down");

        assertThat(store.claimReadyOutboxEvents(10, firstAttemptAt.plusSeconds(1), firstRetryAt.plusSeconds(30))).isEmpty();

        Instant secondAttemptAt = firstRetryAt.plusSeconds(1);
        List<OutboxEvent> secondClaim = store.claimReadyOutboxEvents(10, secondAttemptAt, secondAttemptAt.plusSeconds(30));
        assertThat(secondClaim).hasSize(1);
        assertThat(secondClaim.getFirst().publishAttempts()).isEqualTo(2);

        store.markOutboxEventPublished(claimed.id(), secondAttemptAt);
        OutboxEvent published = store.outboxEvents().getFirst();
        assertThat(published.publishedAt()).isEqualTo(secondAttemptAt);
        assertThat(published.lastPublishError()).isNull();
        assertThat(published.nextPublishAttemptAt()).isNull();
    }
}
