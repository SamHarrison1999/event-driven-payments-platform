package com.samharrison.payments.outbox.internal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.samharrison.payments.outbox.OutboxEventAppender;
import com.samharrison.payments.outbox.OutboxEventRequest;
import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayDeque;
import java.util.Queue;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

@SpringBootTest
@Testcontainers
@Import(
    OutboxPublicationIntegrationTest
        .OutboxTestConfiguration.class
)
@DirtiesContext(
    classMode = DirtiesContext.ClassMode.AFTER_CLASS
)
class OutboxPublicationIntegrationTest {

    private static final Instant START_TIME =
        Instant.parse(
            "2026-07-06T12:00:00.123456Z"
        );

    @Container
    @ServiceConnection
    static final PostgreSQLContainer POSTGRESQL =
        new PostgreSQLContainer(
            "postgres:18.4-alpine"
        )
            .withDatabaseName("outbox_test")
            .withUsername("outbox_test")
            .withPassword("outbox_test_only");

    @Autowired
    private OutboxEventAppender appender;

    @Autowired
    private OutboxPublisher publisher;

    @Autowired
    private OutboxEventRepository repository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private TransactionTemplate transactionTemplate;

    @Autowired
    private MutableClock clock;

    @Autowired
    private StubOutboxTransport transport;

    @BeforeEach
    void cleanDatabase() {
        jdbcTemplate.execute(
            "TRUNCATE TABLE outbox_event"
        );
        clock.setInstant(START_TIME);
        transport.reset();
    }

    @Test
    void persistsAndPublishesAnEvent() {
        UUID eventId = appendEvent();

        OutboxPublicationBatchResult result =
            publisher.publishNextBatch(10);

        OutboxEvent event =
            repository
                .findById(eventId)
                .orElseThrow();

        assertThat(result)
            .isEqualTo(
                new OutboxPublicationBatchResult(
                    1,
                    1,
                    0,
                    0
                )
            );
        assertThat(event.status())
            .isEqualTo(
                OutboxEventStatus.PUBLISHED
            );
        assertThat(event.attemptCount())
            .isEqualTo(1);
        assertThat(event.publishedAt())
            .isEqualTo(START_TIME);
        assertThat(transport.publications())
            .hasSize(1);
    }

    @Test
    void retryableFailureSchedulesAnotherAttempt() {
        UUID eventId = appendEvent();
        transport.retryOnce();

        OutboxPublicationBatchResult first =
            publisher.publishNextBatch(10);

        OutboxEvent pending =
            repository
                .findById(eventId)
                .orElseThrow();

        assertThat(first.retryScheduled())
            .isEqualTo(1);
        assertThat(pending.status())
            .isEqualTo(
                OutboxEventStatus.PENDING
            );
        assertThat(pending.nextAttemptAt())
            .isAfter(START_TIME);

        clock.setInstant(
            pending.nextAttemptAt()
        );

        OutboxPublicationBatchResult second =
            publisher.publishNextBatch(10);

        OutboxEvent published =
            repository
                .findById(eventId)
                .orElseThrow();

        assertThat(second.published())
            .isEqualTo(1);
        assertThat(published.status())
            .isEqualTo(
                OutboxEventStatus.PUBLISHED
            );
        assertThat(published.attemptCount())
            .isEqualTo(2);
    }

    @Test
    void permanentFailureMovesToDeadLetter() {
        UUID eventId = appendEvent();
        transport.failPermanently();

        OutboxPublicationBatchResult result =
            publisher.publishNextBatch(10);

        OutboxEvent event =
            repository
                .findById(eventId)
                .orElseThrow();

        assertThat(result.deadLettered())
            .isEqualTo(1);
        assertThat(event.status())
            .isEqualTo(
                OutboxEventStatus.DEAD_LETTER
            );
        assertThat(event.lastErrorCategory())
            .isEqualTo(
                "PermanentOutboxPublicationException"
            );
    }

    @Test
    void databaseRejectsInvalidJsonPayload() {
        assertThatThrownBy(
            () ->
                jdbcTemplate.update(
                    """
                    INSERT INTO outbox_event (
                        id,
                        aggregate_type,
                        aggregate_id,
                        event_type,
                        schema_version,
                        payload,
                        correlation_id,
                        causation_id,
                        created_at,
                        updated_at,
                        status,
                        attempt_count,
                        next_attempt_at,
                        version
                    )
                    VALUES (
                        ?, ?, ?, ?, ?, ?, ?, ?, ?,
                        ?, ?, ?, ?, ?
                    )
                    """,
                    UUID.randomUUID(),
                    "payment",
                    UUID.randomUUID(),
                    "payment.completed.v1",
                    1,
                    "not-json",
                    "correlation-1",
                    null,
                    START_TIME.atOffset(
                        ZoneOffset.UTC
                    ),
                    START_TIME.atOffset(
                        ZoneOffset.UTC
                    ),
                    "PENDING",
                    0,
                    START_TIME.atOffset(
                        ZoneOffset.UTC
                    ),
                    0L
                )
        )
            .isInstanceOf(
                DataAccessException.class
            );
    }

    private UUID appendEvent() {
        UUID paymentId = UUID.randomUUID();

        return transactionTemplate.execute(
            status ->
                appender.append(
                    new OutboxEventRequest(
                        "payment",
                        paymentId,
                        "payment.completed.v1",
                        1,
                        """
                        {"paymentId":"%s","amountMinorUnits":1250}
                        """
                            .strip()
                            .formatted(paymentId),
                        paymentId.toString()
                    )
                )
        );
    }

    @TestConfiguration
    static class OutboxTestConfiguration {

        @Bean
        @Primary
        MutableClock outboxTestClock() {
            return new MutableClock(START_TIME);
        }

        @Bean
        @Primary
        StubOutboxTransport stubOutboxTransport() {
            return new StubOutboxTransport();
        }
    }

    static final class StubOutboxTransport
        implements OutboxTransport {

        private final Queue<Mode> modes =
            new ArrayDeque<>();

        private final Queue<OutboxPublication>
            publications =
                new ArrayDeque<>();

        void retryOnce() {
            modes.add(Mode.RETRYABLE_FAILURE);
        }

        void failPermanently() {
            modes.add(Mode.PERMANENT_FAILURE);
        }

        Queue<OutboxPublication> publications() {
            return publications;
        }

        void reset() {
            modes.clear();
            publications.clear();
        }

        @Override
        public void publish(
            OutboxPublication publication
        ) {
            Mode mode = modes.poll();

            if (mode == Mode.RETRYABLE_FAILURE) {
                throw new IllegalStateException(
                    "Simulated broker outage"
                );
            }

            if (mode == Mode.PERMANENT_FAILURE) {
                throw new PermanentOutboxPublicationException(
                    "Simulated invalid event"
                );
            }

            publications.add(publication);
        }

        private enum Mode {
            RETRYABLE_FAILURE,
            PERMANENT_FAILURE
        }
    }

    static final class MutableClock extends Clock {

        private final AtomicReference<Instant>
            currentInstant;

        private MutableClock(
            Instant initialInstant
        ) {
            currentInstant =
                new AtomicReference<>(initialInstant);
        }

        void setInstant(
            Instant instant
        ) {
            currentInstant.set(instant);
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(
            ZoneId zone
        ) {
            if (!ZoneOffset.UTC.equals(zone)) {
                throw new IllegalArgumentException(
                    "Only UTC is supported."
                );
            }

            return this;
        }

        @Override
        public Instant instant() {
            return currentInstant.get();
        }
    }
}
