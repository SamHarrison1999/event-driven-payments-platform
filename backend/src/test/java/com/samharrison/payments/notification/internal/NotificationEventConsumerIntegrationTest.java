package com.samharrison.payments.notification.internal;

import static org.assertj.core.api.Assertions.assertThat;

import com.samharrison.payments.outbox.PublishedOutboxCursor;
import com.samharrison.payments.outbox.PublishedOutboxEvent;
import com.samharrison.payments.outbox.PublishedOutboxEventReader;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

@SpringBootTest
@Testcontainers
@DirtiesContext(
    classMode = DirtiesContext.ClassMode.AFTER_CLASS
)
class NotificationEventConsumerIntegrationTest {

    private static final Instant FIRST_PUBLICATION =
        Instant.parse(
            "2026-07-23T12:00:00.123456Z"
        );

    private static final Instant SECOND_PUBLICATION =
        Instant.parse(
            "2026-07-23T12:00:01.123456Z"
        );

    private static final UUID FIRST_EVENT_ID =
        UUID.fromString(
            "00000000-0000-0000-0000-000000000001"
        );

    private static final UUID SECOND_EVENT_ID =
        UUID.fromString(
            "00000000-0000-0000-0000-000000000002"
        );

    @Container
    @ServiceConnection
    static final PostgreSQLContainer POSTGRESQL =
        new PostgreSQLContainer(
            "postgres:18.4-alpine"
        )
            .withDatabaseName("notification_test")
            .withUsername("notification_test")
            .withPassword(
                "notification_test_only"
            );

    @Autowired
    private PublishedOutboxEventReader eventReader;

    @Autowired
    private NotificationEventConsumer consumer;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void resetDatabase() {
        jdbcTemplate.execute(
            """
            TRUNCATE TABLE
                notification_consumer_failure,
                notification,
                outbox_event
            """
        );

        jdbcTemplate.update(
            """
            UPDATE notification_consumer_checkpoint
            SET
                last_published_at = ?,
                last_event_id = ?,
                updated_at = ?,
                version = 0
            WHERE consumer_name = ?
            """,
            offset(Instant.EPOCH),
            new UUID(0L, 0L),
            offset(Instant.EPOCH),
            NotificationEventConsumer.CONSUMER_NAME
        );
    }

    @Test
    void readsPublishedEventsInStablePages() {
        UUID firstPaymentId = UUID.randomUUID();
        UUID secondPaymentId = UUID.randomUUID();

        insertPublishedEvent(
            FIRST_EVENT_ID,
            firstPaymentId,
            "payment.completed.v1",
            1,
            validPayload(
                firstPaymentId,
                UUID.randomUUID()
            ),
            FIRST_PUBLICATION
        );

        insertPublishedEvent(
            SECOND_EVENT_ID,
            secondPaymentId,
            "payment.completed.v1",
            1,
            validPayload(
                secondPaymentId,
                UUID.randomUUID()
            ),
            FIRST_PUBLICATION
        );

        List<PublishedOutboxEvent> firstPage =
            eventReader.readAfter(
                PublishedOutboxCursor.beginning(),
                1
            );

        List<PublishedOutboxEvent> secondPage =
            eventReader.readAfter(
                firstPage.getFirst().cursor(),
                1
            );

        assertThat(firstPage)
            .extracting(
                PublishedOutboxEvent::eventId
            )
            .containsExactly(FIRST_EVENT_ID);

        assertThat(secondPage)
            .extracting(
                PublishedOutboxEvent::eventId
            )
            .containsExactly(SECOND_EVENT_ID);
    }

    @Test
    void createsOneNotificationAndDeduplicatesReplay() {
        UUID paymentId = UUID.randomUUID();
        UUID recipientId = UUID.randomUUID();

        insertPublishedEvent(
            FIRST_EVENT_ID,
            paymentId,
            "payment.completed.v1",
            1,
            validPayload(paymentId, recipientId),
            FIRST_PUBLICATION
        );

        NotificationConsumptionResult first =
            consumer.consumeNextBatch(10);

        assertThat(first)
            .isEqualTo(
                new NotificationConsumptionResult(
                    1,
                    1,
                    0,
                    0,
                    0
                )
            );

        assertThat(notificationCount())
            .isEqualTo(1L);

        assertThat(notificationState())
            .isEqualTo(
                new NotificationState(
                    FIRST_EVENT_ID,
                    recipientId,
                    paymentId,
                    1_234L,
                    "GBP",
                    Instant.parse(
                        "2026-07-23T11:59:30.123456Z"
                    ),
                    "PENDING",
                    0
                )
            );

        jdbcTemplate.update(
            """
            UPDATE outbox_event
            SET
                published_at = ?,
                updated_at = ?,
                version = version + 1
            WHERE id = ?
            """,
            offset(SECOND_PUBLICATION),
            offset(SECOND_PUBLICATION),
            FIRST_EVENT_ID
        );

        NotificationConsumptionResult replay =
            consumer.consumeNextBatch(10);

        assertThat(replay)
            .isEqualTo(
                new NotificationConsumptionResult(
                    1,
                    0,
                    1,
                    0,
                    0
                )
            );

        assertThat(notificationCount())
            .isEqualTo(1L);
    }

    @Test
    void recordsInvalidPayloadAndContinues() {
        UUID invalidPaymentId = UUID.randomUUID();
        UUID validPaymentId = UUID.randomUUID();
        UUID validRecipientId = UUID.randomUUID();

        insertPublishedEvent(
            FIRST_EVENT_ID,
            invalidPaymentId,
            "payment.completed.v1",
            1,
            """
            {"paymentId":"%s"}
            """
                .strip()
                .formatted(invalidPaymentId),
            FIRST_PUBLICATION
        );

        insertPublishedEvent(
            SECOND_EVENT_ID,
            validPaymentId,
            "payment.completed.v1",
            1,
            validPayload(
                validPaymentId,
                validRecipientId
            ),
            SECOND_PUBLICATION
        );

        NotificationConsumptionResult result =
            consumer.consumeNextBatch(10);

        assertThat(result)
            .isEqualTo(
                new NotificationConsumptionResult(
                    2,
                    1,
                    0,
                    1,
                    0
                )
            );

        assertThat(notificationCount())
            .isEqualTo(1L);

        assertThat(
            rowCount(
                "notification_consumer_failure"
            )
        )
            .isEqualTo(1L);

        assertThat(checkpointEventId())
            .isEqualTo(SECOND_EVENT_ID);
    }

    @Test
    void ignoresUnrelatedEventTypesWithoutBlocking() {
        UUID ignoredAggregateId = UUID.randomUUID();
        UUID paymentId = UUID.randomUUID();

        insertPublishedEvent(
            FIRST_EVENT_ID,
            ignoredAggregateId,
            "account.updated.v1",
            1,
            """
            {"accountId":"%s"}
            """
                .strip()
                .formatted(ignoredAggregateId),
            FIRST_PUBLICATION
        );

        insertPublishedEvent(
            SECOND_EVENT_ID,
            paymentId,
            "payment.completed.v1",
            1,
            validPayload(
                paymentId,
                UUID.randomUUID()
            ),
            SECOND_PUBLICATION
        );

        NotificationConsumptionResult result =
            consumer.consumeNextBatch(10);

        assertThat(result)
            .isEqualTo(
                new NotificationConsumptionResult(
                    2,
                    1,
                    0,
                    0,
                    1
                )
            );

        assertThat(checkpointEventId())
            .isEqualTo(SECOND_EVENT_ID);
    }

    private void insertPublishedEvent(
        UUID eventId,
        UUID aggregateId,
        String eventType,
        int schemaVersion,
        String payload,
        Instant publishedAt
    ) {
        Instant createdAt =
            publishedAt.minusSeconds(60);

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
                publication_owner_token,
                publication_lease_expires_at,
                last_error_category,
                last_error_message,
                published_at,
                version
            )
            VALUES (
                ?, ?, ?, ?, ?, ?, ?, ?, ?, ?,
                'PUBLISHED', 1, NULL, NULL, NULL,
                NULL, NULL, ?, 0
            )
            """,
            eventId,
            "payment",
            aggregateId,
            eventType,
            schemaVersion,
            payload,
            eventId.toString(),
            aggregateId.toString(),
            offset(createdAt),
            offset(publishedAt),
            offset(publishedAt)
        );
    }

    private static String validPayload(
        UUID paymentId,
        UUID recipientId
    ) {
        return """
            {"paymentId":"%s","ledgerTransactionId":"%s","actorIdentityId":"%s","sourceAccountId":"%s","destinationAccountId":"%s","amountMinorUnits":1234,"currency":"GBP","completedAt":"2026-07-23T11:59:30.123456Z"}
            """
            .strip()
            .formatted(
                paymentId,
                UUID.randomUUID(),
                recipientId,
                UUID.randomUUID(),
                UUID.randomUUID()
            );
    }

    private long notificationCount() {
        return rowCount("notification");
    }

    private long rowCount(String tableName) {
        Long count =
            jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM " + tableName,
                Long.class
            );

        return count == null ? 0L : count;
    }

    private NotificationState notificationState() {
        return jdbcTemplate.queryForObject(
            """
            SELECT
                source_event_id,
                recipient_identity_user_id,
                payment_id,
                amount_minor_units,
                currency,
                payment_completed_at,
                status,
                attempt_count
            FROM notification
            """,
            (
                resultSet,
                rowNumber
            ) ->
                new NotificationState(
                    resultSet.getObject(
                        "source_event_id",
                        UUID.class
                    ),
                    resultSet.getObject(
                        "recipient_identity_user_id",
                        UUID.class
                    ),
                    resultSet.getObject(
                        "payment_id",
                        UUID.class
                    ),
                    resultSet.getLong(
                        "amount_minor_units"
                    ),
                    resultSet.getString("currency"),
                    resultSet
                        .getObject(
                            "payment_completed_at",
                            OffsetDateTime.class
                        )
                        .toInstant(),
                    resultSet.getString("status"),
                    resultSet.getInt(
                        "attempt_count"
                    )
                )
        );
    }

    private UUID checkpointEventId() {
        return jdbcTemplate.queryForObject(
            """
            SELECT last_event_id
            FROM notification_consumer_checkpoint
            WHERE consumer_name = ?
            """,
            UUID.class,
            NotificationEventConsumer.CONSUMER_NAME
        );
    }

    private static OffsetDateTime offset(
        Instant instant
    ) {
        return instant.atOffset(ZoneOffset.UTC);
    }

    private record NotificationState(
        UUID sourceEventId,
        UUID recipientIdentityUserId,
        UUID paymentId,
        long amountMinorUnits,
        String currency,
        Instant paymentCompletedAt,
        String status,
        int attemptCount
    ) {
    }
}
