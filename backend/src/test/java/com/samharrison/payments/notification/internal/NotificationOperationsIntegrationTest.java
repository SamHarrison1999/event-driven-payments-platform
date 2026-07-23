package com.samharrison.payments.notification.internal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.samharrison.payments.outbox.OutboxDeadLetterSnapshot;
import com.samharrison.payments.outbox.OutboxReplayResult;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.annotation.DirtiesContext;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

@SpringBootTest
@Testcontainers
@DirtiesContext(
    classMode = DirtiesContext.ClassMode.AFTER_CLASS
)
class NotificationOperationsIntegrationTest {

    private static final Instant BASE_TIME =
        Instant.parse(
            "2026-07-23T14:00:00.123456Z"
        );

    @Container
    @ServiceConnection
    static final PostgreSQLContainer POSTGRESQL =
        new PostgreSQLContainer(
            "postgres:18.4-alpine"
        )
            .withDatabaseName(
                "notification_operations_test"
            )
            .withUsername(
                "notification_operations_test"
            )
            .withPassword(
                "notification_operations_test_only"
            );

    @Autowired
    private NotificationQueryService
        notificationQueryService;

    @Autowired
    private OutboxDeadLetterAdminService
        adminService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void resetDatabase() {
        jdbcTemplate.execute(
            """
            TRUNCATE TABLE
                outbox_replay_audit,
                notification_consumer_failure,
                notification,
                outbox_event
            """
        );
    }

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void customerReadsOnlyOwnedNotifications() {
        UUID recipientId = UUID.randomUUID();
        UUID otherRecipientId = UUID.randomUUID();

        UUID newestOwned =
            insertDeliveredNotification(
                recipientId,
                BASE_TIME.plusSeconds(2)
            );

        UUID olderOwned =
            insertDeliveredNotification(
                recipientId,
                BASE_TIME.plusSeconds(1)
            );

        insertDeliveredNotification(
            otherRecipientId,
            BASE_TIME.plusSeconds(3)
        );

        authenticate("CUSTOMER");

        List<NotificationResponse> response =
            notificationQueryService.findOwned(
                recipientId,
                10
            );

        assertThat(response)
            .extracting(
                NotificationResponse::notificationId
            )
            .containsExactly(
                newestOwned,
                olderOwned
            );
    }

    @Test
    void administratorInspectsAndReplaysDeadLetter() {
        UUID eventId = UUID.randomUUID();
        UUID paymentId = UUID.randomUUID();
        UUID actorId = UUID.randomUUID();

        String payload =
            """
            {"paymentId":"%s","amountMinorUnits":1250}
            """
                .strip()
                .formatted(paymentId);

        insertDeadLetter(
            eventId,
            paymentId,
            payload
        );

        authenticate("ADMIN");

        List<OutboxDeadLetterSnapshot> deadLetters =
            adminService.findDeadLetters(10);

        assertThat(deadLetters)
            .singleElement()
            .satisfies(
                event -> {
                    assertThat(event.eventId())
                        .isEqualTo(eventId);
                    assertThat(event.payload())
                        .isEqualTo(payload);
                    assertThat(event.status())
                        .isEqualTo("DEAD_LETTER");
                    assertThat(event.version())
                        .isZero();
                }
            );

        OutboxReplayResult replay =
            adminService.replay(
                eventId,
                actorId,
                new OutboxReplayRequest(
                    "Retry after correcting the simulated sink.",
                    0L
                )
            );

        assertThat(replay.event().eventId())
            .isEqualTo(eventId);

        assertThat(replay.event().payload())
            .isEqualTo(payload);

        assertThat(replay.event().status())
            .isEqualTo("PENDING");

        assertThat(replay.event().attemptCount())
            .isZero();

        assertThat(replay.event().replayCount())
            .isEqualTo(1);

        assertThat(replay.event().lastErrorCategory())
            .isNull();

        assertThat(replay.event().lastErrorMessage())
            .isNull();

        assertThat(replay.event().version())
            .isEqualTo(1L);

        assertThat(
            scalarLong(
                """
                SELECT COUNT(*)
                FROM outbox_replay_audit
                WHERE event_id = ?
                  AND actor_identity_user_id = ?
                """,
                eventId,
                actorId
            )
        )
            .isEqualTo(1L);

        assertThat(
            scalarString(
                """
                SELECT reason
                FROM outbox_replay_audit
                WHERE event_id = ?
                """,
                eventId
            )
        )
            .isEqualTo(
                "Retry after correcting the simulated sink."
            );
    }

    @Test
    void replayRejectsStaleVersionAndNonAdmin() {
        UUID eventId = UUID.randomUUID();

        insertDeadLetter(
            eventId,
            UUID.randomUUID(),
            """
            {"paymentId":"%s","amountMinorUnits":1250}
            """
                .strip()
                .formatted(UUID.randomUUID())
        );

        authenticate("CUSTOMER");

        assertThatThrownBy(
            () ->
                adminService.findDeadLetters(10)
        )
            .isInstanceOf(
                AccessDeniedException.class
            );

        authenticate("ADMIN");

        assertThatThrownBy(
            () ->
                adminService.replay(
                    eventId,
                    UUID.randomUUID(),
                    new OutboxReplayRequest(
                        "Stale operator view.",
                        9L
                    )
                )
        )
            .isInstanceOf(
                com.samharrison.payments.outbox
                    .OutboxReplayConflictException
                    .class
            );
    }

    private UUID insertDeliveredNotification(
        UUID recipientId,
        Instant createdAt
    ) {
        UUID notificationId = UUID.randomUUID();

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
                ?, ?, ?, ?, 1250, 'GBP', ?,
                'DELIVERED', 1, NULL, NULL, NULL,
                NULL, NULL, ?, ?, ?, 0
            )
            """,
            notificationId,
            UUID.randomUUID(),
            recipientId,
            UUID.randomUUID(),
            offset(createdAt.minusSeconds(30)),
            offset(createdAt),
            offset(createdAt),
            offset(createdAt)
        );

        return notificationId;
    }

    private void insertDeadLetter(
        UUID eventId,
        UUID aggregateId,
        String payload
    ) {
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
                replay_count,
                last_replayed_at,
                version
            )
            VALUES (
                ?, 'payment', ?, 'payment.completed.v1',
                1, ?, ?, ?, ?, ?, 'DEAD_LETTER',
                5, NULL, NULL, NULL, ?, ?, NULL,
                0, NULL, 0
            )
            """,
            eventId,
            aggregateId,
            payload,
            eventId.toString(),
            aggregateId.toString(),
            offset(BASE_TIME.minusSeconds(60)),
            offset(BASE_TIME),
            "PermanentOutboxPublicationException",
            "Simulated invalid event"
        );
    }

    private void authenticate(String role) {
        SecurityContextHolder
            .getContext()
            .setAuthentication(
                new UsernamePasswordAuthenticationToken(
                    "integration-user",
                    "not-used",
                    List.of(
                        new SimpleGrantedAuthority(
                            "ROLE_" + role
                        )
                    )
                )
            );
    }

    private long scalarLong(
        String sql,
        Object... arguments
    ) {
        Long value =
            jdbcTemplate.queryForObject(
                sql,
                Long.class,
                arguments
            );

        return value == null ? 0L : value;
    }

    private String scalarString(
        String sql,
        Object... arguments
    ) {
        return jdbcTemplate.queryForObject(
            sql,
            String.class,
            arguments
        );
    }

    private static OffsetDateTime offset(
        Instant instant
    ) {
        return instant.atOffset(ZoneOffset.UTC);
    }
}
