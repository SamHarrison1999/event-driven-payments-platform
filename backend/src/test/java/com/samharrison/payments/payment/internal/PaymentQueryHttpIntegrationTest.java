package com.samharrison.payments.payment.internal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
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
class PaymentQueryHttpIntegrationTest {

    private static final String PAYMENT_ENDPOINT =
        "/api/v1/payments";

    private static final String REGISTRATION_ENDPOINT =
        "/api/v1/identity/registrations";

    private static final String SESSION_ENDPOINT =
        "/api/v1/identity/session";

    private static final String SESSION_COOKIE_NAME =
        "PAYMENTS_SESSION";

    private static final String PASSWORD =
        "this is a secure customer passphrase";

    private static final Instant CREATED_AT =
        Instant.parse(
            "2026-07-03T12:00:00Z"
        );

    @Container
    @ServiceConnection
    static final PostgreSQLContainer POSTGRESQL =
        new PostgreSQLContainer(
            "postgres:18.4-alpine"
        )
            .withDatabaseName(
                "payments_query_http_test"
            )
            .withUsername("payments_test")
            .withPassword("payments_test_only");

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void clearStoredData() {
        truncatePaymentData();

        jdbcTemplate.update(
            "DELETE FROM spring_session_attributes"
        );

        jdbcTemplate.update(
            "DELETE FROM spring_session"
        );

        jdbcTemplate.update(
            "DELETE FROM identity_user"
        );
    }

    @Test
    void customerReadsOwnPaymentWithoutActorLeak()
        throws Exception {
        UserSession customer =
            createSession(
                "query-customer@example.com",
                null
            );

        UUID paymentId =
            insertPayment(
                customer.userId(),
                "PENDING",
                null,
                null
            );

        mockMvc.perform(
                get(paymentEndpoint(paymentId))
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
            .andExpect(
                jsonPath("$.paymentId")
                    .value(paymentId.toString())
            )
            .andExpect(
                jsonPath("$.amountMinorUnits")
                    .value(250)
            )
            .andExpect(
                jsonPath("$.currency")
                    .value("GBP")
            )
            .andExpect(
                jsonPath("$.status")
                    .value("PENDING")
            )
            .andExpect(
                jsonPath("$.version")
                    .value(0)
            )
            .andExpect(
                jsonPath("$.actorIdentityId")
                    .doesNotExist()
            );
    }

    @Test
    void rejectedPaymentExposesStableReasonCode()
        throws Exception {
        UserSession customer =
            createSession(
                "query-rejected@example.com",
                null
            );

        UUID paymentId =
            insertPayment(
                customer.userId(),
                "REJECTED",
                "INSUFFICIENT_FUNDS",
                null
            );

        mockMvc.perform(
                get(paymentEndpoint(paymentId))
                    .cookie(customer.cookie())
            )
            .andExpect(status().isOk())
            .andExpect(
                jsonPath("$.status")
                    .value("REJECTED")
            )
            .andExpect(
                jsonPath("$.rejectionReason")
                    .value(
                        "PAYMENT_INSUFFICIENT_FUNDS"
                    )
            )
            .andExpect(
                jsonPath("$.failureReason")
                    .doesNotExist()
            )
            .andExpect(
                jsonPath("$.ledgerTransactionId")
                    .doesNotExist()
            );
    }

    @Test
    void foreignPaymentIsIndistinguishableFromMissing()
        throws Exception {
        UserSession requestingCustomer =
            createSession(
                "query-requester@example.com",
                null
            );

        UserSession owningCustomer =
            createSession(
                "query-owner@example.com",
                null
            );

        UUID paymentId =
            insertPayment(
                owningCustomer.userId(),
                "PENDING",
                null,
                null
            );

        MvcResult foreignResult =
            mockMvc.perform(
                    get(paymentEndpoint(paymentId))
                        .cookie(
                            requestingCustomer.cookie()
                        )
                )
                .andExpect(status().isNotFound())
                .andExpect(
                    content().contentTypeCompatibleWith(
                        MediaType.APPLICATION_PROBLEM_JSON
                    )
                )
                .andExpect(
                    jsonPath("$.code")
                        .value("PAYMENT_NOT_FOUND")
                )
                .andReturn();

        truncatePaymentData();

        MvcResult missingResult =
            mockMvc.perform(
                    get(paymentEndpoint(paymentId))
                        .cookie(
                            requestingCustomer.cookie()
                        )
                )
                .andExpect(status().isNotFound())
                .andExpect(
                    jsonPath("$.code")
                        .value("PAYMENT_NOT_FOUND")
                )
                .andReturn();

        assertThat(
            foreignResult
                .getResponse()
                .getContentAsString()
        )
            .isEqualTo(
                missingResult
                    .getResponse()
                    .getContentAsString()
            );
    }

    @Test
    void operationsAndAdminReadAnyPayment()
        throws Exception {
        UserSession owner =
            createSession(
                "query-payment-owner@example.com",
                null
            );

        UserSession operations =
            createSession(
                "query-operations@example.com",
                "OPERATIONS"
            );

        UserSession administrator =
            createSession(
                "query-admin@example.com",
                "ADMIN"
            );

        UUID paymentId =
            insertPayment(
                owner.userId(),
                "PENDING",
                null,
                null
            );

        mockMvc.perform(
                get(paymentEndpoint(paymentId))
                    .cookie(operations.cookie())
            )
            .andExpect(status().isOk());

        mockMvc.perform(
                get(paymentEndpoint(paymentId))
                    .cookie(administrator.cookie())
            )
            .andExpect(status().isOk());
    }

    @Test
    void reconciliationAnalystHasNoReadPermission()
        throws Exception {
        UserSession owner =
            createSession(
                "query-analyst-owner@example.com",
                null
            );

        UserSession analyst =
            createSession(
                "query-analyst@example.com",
                "RECONCILIATION_ANALYST"
            );

        UUID paymentId =
            insertPayment(
                owner.userId(),
                "PENDING",
                null,
                null
            );

        mockMvc.perform(
                get(paymentEndpoint(paymentId))
                    .cookie(analyst.cookie())
            )
            .andExpect(status().isForbidden())
            .andExpect(
                jsonPath("$.code")
                    .value("SECURITY_ACCESS_DENIED")
            );
    }

    @Test
    void anonymousUserCannotReadPayment()
        throws Exception {
        mockMvc.perform(
                get(
                    paymentEndpoint(
                        UUID.randomUUID()
                    )
                )
            )
            .andExpect(status().isUnauthorized())
            .andExpect(
                jsonPath("$.code")
                    .value(
                        "SECURITY_AUTHENTICATION_REQUIRED"
                    )
            );
    }

    @Test
    void malformedPaymentIdentifierReturnsProblem()
        throws Exception {
        UserSession customer =
            createSession(
                "query-invalid-id@example.com",
                null
            );

        mockMvc.perform(
                get(PAYMENT_ENDPOINT + "/not-a-uuid")
                    .cookie(customer.cookie())
            )
            .andExpect(status().isBadRequest())
            .andExpect(
                jsonPath("$.code")
                    .value(
                        "PAYMENT_IDENTIFIER_INVALID"
                    )
            );
    }

    @Test
    void openApiDocumentsPaymentLookup()
        throws Exception {
        mockMvc.perform(
                get("/v3/api-docs")
            )
            .andExpect(status().isOk())
            .andExpect(
                jsonPath(
                    "$.paths['/api/v1/payments/{paymentId}']"
                        + ".get"
                )
                    .exists()
            );
    }

    private UserSession createSession(
        String email,
        String replacementRole
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

        assertThat(userId)
            .isNotNull();

        if (replacementRole != null) {
            jdbcTemplate.update(
                """
                DELETE FROM identity_user_role
                WHERE user_id = ?
                """,
                userId
            );

            jdbcTemplate.update(
                """
                INSERT INTO identity_user_role (
                    user_id,
                    role_code
                )
                VALUES (?, ?)
                """,
                userId,
                replacementRole
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

        assertThat(sessionCookie)
            .isNotNull();

        return sessionCookie;
    }

    private UUID insertPayment(
        UUID actorIdentityId,
        String status,
        String rejectionReason,
        String failureReason
    ) {
        UUID paymentId =
            UUID.randomUUID();

        jdbcTemplate.update(
            """
            INSERT INTO payment (
                id,
                actor_identity_id,
                source_account_id,
                destination_account_id,
                amount_minor_units,
                currency,
                status,
                ledger_transaction_id,
                rejection_reason,
                failure_reason,
                created_at,
                updated_at,
                version
            )
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            """,
            paymentId,
            actorIdentityId,
            UUID.randomUUID(),
            UUID.randomUUID(),
            250L,
            "GBP",
            status,
            null,
            rejectionReason,
            failureReason,
            CREATED_AT.atOffset(
                ZoneOffset.UTC
            ),
            CREATED_AT.atOffset(
                ZoneOffset.UTC
            ),
            0L
        );

        return paymentId;
    }

    private void truncatePaymentData() {
        jdbcTemplate.execute(
            """
            TRUNCATE TABLE
                payment_idempotency,
                payment,
                ledger_entry,
                ledger_transaction
            """
        );
    }

    private static String paymentEndpoint(
        UUID paymentId
    ) {
        return PAYMENT_ENDPOINT
            + "/"
            + paymentId;
    }

    private record UserSession(
        UUID userId,
        Cookie cookie
    ) {
    }
}
