package com.samharrison.payments.notification.internal;

import static org.assertj.core.api.Assertions.assertThat;

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
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

@SpringBootTest
@Testcontainers
@Import(
    NotificationDeliveryIntegrationTest
        .NotificationDeliveryTestConfiguration
        .class
)
@DirtiesContext(
    classMode = DirtiesContext.ClassMode.AFTER_CLASS
)
class NotificationDeliveryIntegrationTest {

    private static final Instant START_TIME =
        Instant.parse(
            "2026-07-23T13:00:00.123456Z"
        );

    @Container
    @ServiceConnection
    static final PostgreSQLContainer POSTGRESQL =
        new PostgreSQLContainer(
            "postgres:18.4-alpine"
        )
            .withDatabaseName(
                "notification_delivery_test"
            )
            .withUsername(
                "notification_delivery_test"
            )
            .withPassword(
                "notification_delivery_test_only"
            );

    @Autowired
    private NotificationDeliveryProcessor processor;

    @Autowired
    private NotificationRepository repository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private MutableClock clock;

    @Autowired
    private StubNotificationTransport transport;

    @BeforeEach
    void resetDatabase() {
        jdbcTemplate.execute(
            "TRUNCATE TABLE notification"
        );

        clock.setInstant(START_TIME);
        transport.reset();
    }

    @Test
    void deliversPendingNotification() {
        UUID notificationId =
            insertPendingNotification(0);

        NotificationDeliveryBatchResult result =
            processor.deliverNextBatch(10);

        Notification notification =
            repository
                .findById(notificationId)
                .orElseThrow();

        assertThat(result)
            .isEqualTo(
                new NotificationDeliveryBatchResult(
                    1,
                    1,
                    0,
                    0
                )
            );

        assertThat(notification.status())
            .isEqualTo(
                NotificationStatus.DELIVERED
            );

        assertThat(notification.attemptCount())
            .isEqualTo(1);

        assertThat(notification.deliveredAt())
            .isEqualTo(START_TIME);

        assertThat(transport.deliveries())
            .hasSize(1);
    }

    @Test
    void retryableFailureSchedulesAnotherAttempt() {
        UUID notificationId =
            insertPendingNotification(0);

        transport.retryOnce();

        NotificationDeliveryBatchResult first =
            processor.deliverNextBatch(10);

        Notification pending =
            repository
                .findById(notificationId)
                .orElseThrow();

        assertThat(first.retryScheduled())
            .isEqualTo(1);

        assertThat(pending.status())
            .isEqualTo(NotificationStatus.PENDING);

        assertThat(pending.nextAttemptAt())
            .isAfter(START_TIME);

        clock.setInstant(pending.nextAttemptAt());

        NotificationDeliveryBatchResult second =
            processor.deliverNextBatch(10);

        Notification delivered =
            repository
                .findById(notificationId)
                .orElseThrow();

        assertThat(second.delivered())
            .isEqualTo(1);

        assertThat(delivered.status())
            .isEqualTo(
                NotificationStatus.DELIVERED
            );

        assertThat(delivered.attemptCount())
            .isEqualTo(2);
    }

    @Test
    void permanentFailureMovesToDeadLetter() {
        UUID notificationId =
            insertPendingNotification(0);

        transport.failPermanently();

        NotificationDeliveryBatchResult result =
            processor.deliverNextBatch(10);

        Notification notification =
            repository
                .findById(notificationId)
                .orElseThrow();

        assertThat(result.deadLettered())
            .isEqualTo(1);

        assertThat(notification.status())
            .isEqualTo(
                NotificationStatus.DEAD_LETTER
            );

        assertThat(notification.lastErrorCategory())
            .isEqualTo(
                "PermanentNotificationDeliveryException"
            );
    }

    @Test
    void expiredDeliveryLeaseCanBeReclaimed() {
        UUID notificationId =
            insertExpiredDeliveringNotification();

        NotificationDeliveryBatchResult result =
            processor.deliverNextBatch(10);

        Notification notification =
            repository
                .findById(notificationId)
                .orElseThrow();

        assertThat(result.delivered())
            .isEqualTo(1);

        assertThat(notification.status())
            .isEqualTo(
                NotificationStatus.DELIVERED
            );

        assertThat(notification.attemptCount())
            .isEqualTo(2);
    }

    @Test
    void exhaustedRetryMovesToDeadLetter() {
        UUID notificationId =
            insertPendingNotification(
                Notification.MAX_ATTEMPTS - 1
            );

        transport.retryOnce();

        NotificationDeliveryBatchResult result =
            processor.deliverNextBatch(10);

        Notification notification =
            repository
                .findById(notificationId)
                .orElseThrow();

        assertThat(result.deadLettered())
            .isEqualTo(1);

        assertThat(notification.status())
            .isEqualTo(
                NotificationStatus.DEAD_LETTER
            );

        assertThat(notification.attemptCount())
            .isEqualTo(
                Notification.MAX_ATTEMPTS
            );
    }

    private UUID insertPendingNotification(
        int attemptCount
    ) {
        UUID notificationId = UUID.randomUUID();

        insertNotification(
            notificationId,
            "PENDING",
            attemptCount,
            offset(START_TIME),
            null,
            null,
            offset(START_TIME),
            null
        );

        return notificationId;
    }

    private UUID
        insertExpiredDeliveringNotification() {
        UUID notificationId = UUID.randomUUID();

        Instant updatedAt =
            START_TIME.minusSeconds(60);

        insertNotification(
            notificationId,
            "DELIVERING",
            1,
            null,
            UUID.randomUUID(),
            offset(
                START_TIME.minusSeconds(30)
            ),
            offset(updatedAt),
            null
        );

        return notificationId;
    }

    private void insertNotification(
        UUID notificationId,
        String status,
        int attemptCount,
        OffsetDateTime nextAttemptAt,
        UUID ownerToken,
        OffsetDateTime leaseExpiresAt,
        OffsetDateTime updatedAt,
        OffsetDateTime deliveredAt
    ) {
        Instant createdAt =
            START_TIME.minusSeconds(120);

        jdbcTemplate.update(
            """
            INSERT INTO notification (
                id,
                source_event_id,
                recipient_identity_user_id,
                payment_id,
                amount_minor_units,
                currency,
                payment_completed_at,
                status,
                attempt_count,
                next_attempt_at,
                delivery_owner_token,
                delivery_lease_expires_at,
                last_error_category,
                last_error_message,
                created_at,
                updated_at,
                delivered_at,
                version
            )
            VALUES (
                ?, ?, ?, ?, ?, ?, ?, ?, ?, ?,
                ?, ?, NULL, NULL, ?, ?, ?, 0
            )
            """,
            notificationId,
            UUID.randomUUID(),
            UUID.randomUUID(),
            UUID.randomUUID(),
            1_250L,
            "GBP",
            offset(
                createdAt.minusSeconds(30)
            ),
            status,
            attemptCount,
            nextAttemptAt,
            ownerToken,
            leaseExpiresAt,
            offset(createdAt),
            updatedAt,
            deliveredAt
        );
    }

    private static OffsetDateTime offset(
        Instant instant
    ) {
        return instant.atOffset(ZoneOffset.UTC);
    }

    @TestConfiguration
    static class
        NotificationDeliveryTestConfiguration {

        @Bean
        @Primary
        MutableClock notificationDeliveryClock() {
            return new MutableClock(START_TIME);
        }

        @Bean
        @Primary
        StubNotificationTransport
            stubNotificationTransport() {
            return new StubNotificationTransport();
        }
    }

    static final class StubNotificationTransport
        implements NotificationTransport {

        private final Queue<Mode> modes =
            new ArrayDeque<>();

        private final Queue<NotificationDelivery>
            deliveries =
                new ArrayDeque<>();

        void retryOnce() {
            modes.add(Mode.RETRYABLE_FAILURE);
        }

        void failPermanently() {
            modes.add(Mode.PERMANENT_FAILURE);
        }

        Queue<NotificationDelivery> deliveries() {
            return deliveries;
        }

        void reset() {
            modes.clear();
            deliveries.clear();
        }

        @Override
        public void deliver(
            NotificationDelivery delivery
        ) {
            Mode mode = modes.poll();

            if (mode == Mode.RETRYABLE_FAILURE) {
                throw new IllegalStateException(
                    "Simulated notification outage"
                );
            }

            if (mode == Mode.PERMANENT_FAILURE) {
                throw new PermanentNotificationDeliveryException(
                    "Simulated invalid notification"
                );
            }

            deliveries.add(delivery);
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
