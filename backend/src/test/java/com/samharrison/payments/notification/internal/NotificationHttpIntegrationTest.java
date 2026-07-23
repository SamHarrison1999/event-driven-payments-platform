package com.samharrison.payments.notification.internal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import jakarta.servlet.http.Cookie;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
@DirtiesContext(
    classMode = DirtiesContext.ClassMode.AFTER_CLASS
)
class NotificationHttpIntegrationTest {

    private static final String NOTIFICATION_ENDPOINT =
        "/api/v1/notifications";

    private static final String DEAD_LETTER_ENDPOINT =
        "/api/v1/admin/outbox/dead-letters";

    private static final String REGISTRATION_ENDPOINT =
        "/api/v1/identity/registrations";

    private static final String SESSION_ENDPOINT =
        "/api/v1/identity/session";

    private static final String SESSION_COOKIE_NAME =
        "PAYMENTS_SESSION";

    private static final String PASSWORD =
        "this is a secure customer passphrase";

    private static final Instant BASE_TIME =
        Instant.parse(
            "2026-07-23T15:00:00.123456Z"
        );

    @Container
    @ServiceConnection
    static final PostgreSQLContainer POSTGRESQL =
        new PostgreSQLContainer(
            "postgres:18.4-alpine"
        )
            .withDatabaseName(
                "notification_http_test"
            )
            .withUsername("payments_test")
            .withPassword("payments_test_only");

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void clearStoredData() {
        jdbcTemplate.execute(
            """
            TRUNCATE TABLE
                outbox_replay_audit,
                notification_consumer_failure,
                notification,
                outbox_event,
                spring_session_attributes,
                spring_session,
                identity_security_event,
                identity_user_role,
                identity_user
            CASCADE
            """
        );
    }

    @Test
    void customerReadsOnlyOwnedNotifications()
        throws Exception {
        UserSession customer =
            createSession(
                "notification-customer@example.com",
                null
            );

        UserSession otherCustomer =
            createSession(
                "notification-other@example.com",
                null
            );

        UUID newestOwned =
            insertDeliveredNotification(
                customer.userId(),
                BASE_TIME.plusSeconds(2)
            );

        UUID olderOwned =
            insertDeliveredNotification(
                customer.userId(),
                BASE_TIME.plusSeconds(1)
            );

        insertDeliveredNotification(
            otherCustomer.userId(),
            BASE_TIME.plusSeconds(3)
        );

        mockMvc.perform(
                get(NOTIFICATION_ENDPOINT)
                    .cookie(customer.cookie())
            )
            .andExpect(status().isOk())
            .andExpect(
                content().contentTypeCompatibleWith(
                    MediaType.APPLICATION_JSON
                )
            )
            .andExpect(
                header().string(
                    HttpHeaders.CACHE_CONTROL,
                    containsString("no-store")
                )
            )
            .andExpect(jsonPath("$", hasSize(2)))
            .andExpect(
                jsonPath("$[0].notificationId")
                    .value(newestOwned.toString())
            )
            .andExpect(
                jsonPath("$[0].amountMinorUnits")
                    .value(1250)
            )
            .andExpect(
                jsonPath("$[0].currency")
                    .value("GBP")
            )
            .andExpect(
                jsonPath("$[0].status")
                    .value("DELIVERED")
            )
            .andExpect(
                jsonPath("$[1].notificationId")
                    .value(olderOwned.toString())
            );
    }

    @Test
    void notificationEndpointRequiresAuthentication()
        throws Exception {
        mockMvc.perform(
                get(NOTIFICATION_ENDPOINT)
            )
            .andExpect(status().isUnauthorized())
            .andExpect(
                content().contentTypeCompatibleWith(
                    MediaType.APPLICATION_PROBLEM_JSON
                )
            )
            .andExpect(
                jsonPath("$.code")
                    .value(
                        "SECURITY_AUTHENTICATION_REQUIRED"
                    )
            );
    }

    @Test
    void administratorInspectsAndReplaysDeadLetter()
        throws Exception {
        UserSession administrator =
            createSession(
                "notification-admin@example.com",
                "ADMIN"
            );

        UUID eventId = insertDeadLetter();

        mockMvc.perform(
                get(DEAD_LETTER_ENDPOINT)
                    .cookie(administrator.cookie())
            )
            .andExpect(status().isOk())
            .andExpect(jsonPath("$", hasSize(1)))
            .andExpect(
                jsonPath("$[0].eventId")
                    .value(eventId.toString())
            )
            .andExpect(
                jsonPath("$[0].status")
                    .value("DEAD_LETTER")
            )
            .andExpect(
                jsonPath("$[0].version")
                    .value(0)
            );

        mockMvc.perform(
                post(
                    DEAD_LETTER_ENDPOINT
                        + "/"
                        + eventId
                        + "/replay"
                )
                    .cookie(administrator.cookie())
                    .with(csrf())
                    .contentType(
                        MediaType.APPLICATION_JSON
                    )
                    .content(
                        """
                        {
                          "reason": "Retry after simulated sink repair.",
                          "expectedVersion": 0
                        }
                        """
                    )
            )
            .andExpect(status().isOk())
            .andExpect(
                header().string(
                    HttpHeaders.CACHE_CONTROL,
                    containsString("no-store")
                )
            )
            .andExpect(
                jsonPath("$.event.eventId")
                    .value(eventId.toString())
            )
            .andExpect(
                jsonPath("$.event.status")
                    .value("PENDING")
            )
            .andExpect(
                jsonPath("$.event.replayCount")
                    .value(1)
            )
            .andExpect(
                jsonPath("$.event.lastErrorCategory")
                    .doesNotExist()
            )
            .andExpect(
                jsonPath("$.replayAuditId")
                    .isNotEmpty()
            );

        assertThat(
            replayAuditCount(eventId)
        )
            .isEqualTo(1L);
    }

    @Test
    void replayRequiresAdministratorAndCsrf()
        throws Exception {
        UserSession customer =
            createSession(
                "notification-forbidden@example.com",
                null
            );

        UserSession administrator =
            createSession(
                "notification-csrf-admin@example.com",
                "ADMIN"
            );

        UUID eventId = insertDeadLetter();

        mockMvc.perform(
                get(DEAD_LETTER_ENDPOINT)
                    .cookie(customer.cookie())
            )
            .andExpect(status().isForbidden())
            .andExpect(
                content().contentTypeCompatibleWith(
                    MediaType.APPLICATION_PROBLEM_JSON
                )
            )
            .andExpect(
                jsonPath("$.code")
                    .value("SECURITY_ACCESS_DENIED")
            );

        mockMvc.perform(
                post(
                    DEAD_LETTER_ENDPOINT
                        + "/"
                        + eventId
                        + "/replay"
                )
                    .cookie(administrator.cookie())
                    .contentType(
                        MediaType.APPLICATION_JSON
                    )
                    .content(
                        """
                        {
                          "reason": "No CSRF token.",
                          "expectedVersion": 0
                        }
                        """
                    )
            )
            .andExpect(status().isForbidden());

        assertThat(
            replayAuditCount(eventId)
        )
            .isZero();
    }

    private UserSession createSession(
        String email,
        String additionalRole
    ) throws Exception {
        register(email);

        UUID userId =
            jdbcTemplate.queryForObject(
                """
                SELECT id
                FROM identity_user
                WHERE normalized_email = ?
                """,
                UUID.class,
                email
            );

        assertThat(userId).isNotNull();

        if (additionalRole != null) {
            jdbcTemplate.update(
                """
                INSERT INTO identity_user_role (
                    user_id,
                    role_code
                )
                VALUES (?, ?)
                """,
                userId,
                additionalRole
            );
        }

        return new UserSession(
            userId,
            login(email)
        );
    }

    private void register(
        String email
    ) throws Exception {
        mockMvc.perform(
                post(REGISTRATION_ENDPOINT)
                    .with(csrf())
                    .contentType(
                        MediaType.APPLICATION_JSON
                    )
                    .content(
                        """
                        {
                          "email": "%s",
                          "password": "%s"
                        }
                        """
                            .formatted(
                                email,
                                PASSWORD
                            )
                    )
            )
            .andExpect(status().isCreated());
    }

    private Cookie login(
        String email
    ) throws Exception {
        MvcResult result =
            mockMvc.perform(
                    post(SESSION_ENDPOINT)
                        .with(csrf())
                        .contentType(
                            MediaType.APPLICATION_JSON
                        )
                        .content(
                            """
                            {
                              "email": "%s",
                              "password": "%s"
                            }
                            """
                                .formatted(
                                    email,
                                    PASSWORD
                                )
                        )
                )
                .andExpect(status().isOk())
                .andReturn();

        Cookie sessionCookie =
            result
                .getResponse()
                .getCookie(
                    SESSION_COOKIE_NAME
                );

        assertThat(sessionCookie).isNotNull();
        return sessionCookie;
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
            createdAt
                .minusSeconds(30)
                .atOffset(ZoneOffset.UTC),
            createdAt.atOffset(ZoneOffset.UTC),
            createdAt.atOffset(ZoneOffset.UTC),
            createdAt.atOffset(ZoneOffset.UTC)
        );

        return notificationId;
    }

    private UUID insertDeadLetter() {
        UUID eventId = UUID.randomUUID();
        UUID paymentId = UUID.randomUUID();

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
            paymentId,
            """
            {"paymentId":"%s","amountMinorUnits":1250}
            """
                .strip()
                .formatted(paymentId),
            eventId.toString(),
            paymentId.toString(),
            BASE_TIME
                .minusSeconds(60)
                .atOffset(ZoneOffset.UTC),
            BASE_TIME.atOffset(ZoneOffset.UTC),
            "PermanentOutboxPublicationException",
            "Simulated invalid event"
        );

        return eventId;
    }

    private long replayAuditCount(
        UUID eventId
    ) {
        Long count =
            jdbcTemplate.queryForObject(
                """
                SELECT COUNT(*)
                FROM outbox_replay_audit
                WHERE event_id = ?
                """,
                Long.class,
                eventId
            );

        return count == null ? 0L : count;
    }

    private record UserSession(
        UUID userId,
        Cookie cookie
    ) {
    }
}
