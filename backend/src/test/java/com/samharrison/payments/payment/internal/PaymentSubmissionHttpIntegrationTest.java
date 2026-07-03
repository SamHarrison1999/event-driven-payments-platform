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
import java.util.Objects;
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
import org.springframework.test.web.servlet.ResultActions;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
@DirtiesContext(
    classMode = DirtiesContext.ClassMode.AFTER_CLASS
)
class PaymentSubmissionHttpIntegrationTest {

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

    private static final Instant FIXTURE_TIME =
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
                "payments_submission_http_test"
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
                payment_idempotency,
                payment,
                ledger_entry,
                ledger_transaction
            """
        );

        jdbcTemplate.update(
            "DELETE FROM customer_identity_assignment"
        );

        jdbcTemplate.update(
            "DELETE FROM customer_account"
        );

        jdbcTemplate.update(
            "DELETE FROM customer_profile"
        );

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
    void customerSubmitsAndReplaysCompletedPayment()
        throws Exception {
        UserSession customer =
            createSession(
                "payment-customer@example.com",
                null
            );

        PaymentAccounts accounts =
            createOwnedPaymentAccounts(
                customer.userId(),
                1_000L,
                100L
            );

        MvcResult first =
            submit(
                customer.cookie(),
                "http-payment-1001",
                accounts,
                250L,
                true
            )
                .andExpect(status().isCreated())
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
                        .isNotEmpty()
                )
                .andExpect(
                    jsonPath("$.status")
                        .value("COMPLETED")
                )
                .andExpect(
                    jsonPath("$.ledgerTransactionId")
                        .isNotEmpty()
                )
                .andReturn();

        String firstBody =
            first.getResponse().getContentAsString();

        MvcResult replay =
            submit(
                customer.cookie(),
                "http-payment-1001",
                accounts,
                250L,
                true
            )
                .andExpect(status().isCreated())
                .andExpect(
                    content().contentTypeCompatibleWith(
                        MediaType.APPLICATION_JSON
                    )
                )
                .andReturn();

        assertThat(
            replay.getResponse().getContentAsString()
        )
            .isEqualTo(firstBody);

        assertThat(countRows("payment"))
            .isEqualTo(1L);

        assertThat(countRows("payment_idempotency"))
            .isEqualTo(1L);

        assertThat(countRows("ledger_transaction"))
            .isEqualTo(1L);

        assertThat(countRows("ledger_entry"))
            .isEqualTo(2L);

        assertThat(
            accountBalance(accounts.sourceAccountId())
        )
            .isEqualTo(750L);

        assertThat(
            accountBalance(
                accounts.destinationAccountId()
            )
        )
            .isEqualTo(350L);
    }

    @Test
    void differentRequestWithSameKeyConflicts()
        throws Exception {
        UserSession customer =
            createSession(
                "payment-conflict@example.com",
                null
            );

        PaymentAccounts accounts =
            createOwnedPaymentAccounts(
                customer.userId(),
                1_000L,
                100L
            );

        submit(
            customer.cookie(),
            "http-payment-conflict",
            accounts,
            100L,
            true
        )
            .andExpect(status().isCreated());

        submit(
            customer.cookie(),
            "http-payment-conflict",
            accounts,
            200L,
            true
        )
            .andExpect(status().isConflict())
            .andExpect(
                content().contentTypeCompatibleWith(
                    MediaType.APPLICATION_PROBLEM_JSON
                )
            )
            .andExpect(
                jsonPath("$.code")
                    .value(
                        "IDEMPOTENCY_KEY_REUSED"
                    )
            );

        assertThat(countRows("payment"))
            .isEqualTo(1L);

        assertThat(countRows("ledger_transaction"))
            .isEqualTo(1L);
    }

    @Test
    void insufficientFundsResponseIsReplayable()
        throws Exception {
        UserSession customer =
            createSession(
                "payment-rejected@example.com",
                null
            );

        PaymentAccounts accounts =
            createOwnedPaymentAccounts(
                customer.userId(),
                50L,
                100L
            );

        MvcResult first =
            submit(
                customer.cookie(),
                "http-payment-rejected",
                accounts,
                75L,
                true
            )
                .andExpect(
                    status().isUnprocessableContent()
                )
                .andExpect(
                    content().contentTypeCompatibleWith(
                        MediaType.APPLICATION_PROBLEM_JSON
                    )
                )
                .andExpect(
                    jsonPath("$.code")
                        .value(
                            "PAYMENT_INSUFFICIENT_FUNDS"
                        )
                )
                .andReturn();

        MvcResult replay =
            submit(
                customer.cookie(),
                "http-payment-rejected",
                accounts,
                75L,
                true
            )
                .andExpect(
                    status().isUnprocessableContent()
                )
                .andReturn();

        assertThat(
            replay.getResponse().getContentAsString()
        )
            .isEqualTo(
                first.getResponse().getContentAsString()
            );

        assertThat(countRows("payment"))
            .isEqualTo(1L);

        assertThat(countRows("ledger_transaction"))
            .isZero();

        assertThat(
            accountBalance(accounts.sourceAccountId())
        )
            .isEqualTo(50L);
    }

    @Test
    void missingIdempotencyHeaderCreatesNoReservation()
        throws Exception {
        UserSession customer =
            createSession(
                "payment-missing-key@example.com",
                null
            );

        PaymentAccounts accounts =
            createOwnedPaymentAccounts(
                customer.userId(),
                1_000L,
                100L
            );

        submit(
            customer.cookie(),
            null,
            accounts,
            100L,
            true
        )
            .andExpect(status().isBadRequest())
            .andExpect(
                jsonPath("$.code")
                    .value(
                        "PAYMENT_IDEMPOTENCY_KEY_REQUIRED"
                    )
            );

        assertThat(countRows("payment"))
            .isZero();

        assertThat(countRows("payment_idempotency"))
            .isZero();
    }

    @Test
    void invalidIdempotencyHeaderCreatesNoReservation()
        throws Exception {
        UserSession customer =
            createSession(
                "payment-invalid-key@example.com",
                null
            );

        PaymentAccounts accounts =
            createOwnedPaymentAccounts(
                customer.userId(),
                1_000L,
                100L
            );

        submit(
            customer.cookie(),
            "invalid key",
            accounts,
            100L,
            true
        )
            .andExpect(status().isBadRequest())
            .andExpect(
                jsonPath("$.code")
                    .value(
                        "PAYMENT_IDEMPOTENCY_KEY_INVALID"
                    )
            );

        assertThat(countRows("payment"))
            .isZero();
    }

    @Test
    void sameAccountRequestCreatesNoReservation()
        throws Exception {
        UserSession customer =
            createSession(
                "payment-same-account@example.com",
                null
            );

        PaymentAccounts accounts =
            createOwnedPaymentAccounts(
                customer.userId(),
                1_000L,
                100L
            );

        PaymentAccounts sameAccount =
            new PaymentAccounts(
                accounts.sourceAccountId(),
                accounts.sourceAccountId()
            );

        submit(
            customer.cookie(),
            "http-payment-same-account",
            sameAccount,
            100L,
            true
        )
            .andExpect(status().isBadRequest())
            .andExpect(
                jsonPath("$.code")
                    .value("PAYMENT_REQUEST_INVALID")
            );

        assertThat(countRows("payment"))
            .isZero();

        assertThat(countRows("payment_idempotency"))
            .isZero();
    }

    @Test
    void malformedRequestCreatesNoReservation()
        throws Exception {
        UserSession customer =
            createSession(
                "payment-malformed@example.com",
                null
            );

        mockMvc.perform(
                post(PAYMENT_ENDPOINT)
                    .cookie(customer.cookie())
                    .with(csrf())
                    .header(
                        PaymentIdempotencyHeader.NAME,
                        "http-payment-malformed"
                    )
                    .contentType(
                        MediaType.APPLICATION_JSON
                    )
                    .content(
                        """
                        {
                          "sourceAccountId": "not-a-uuid"
                        }
                        """
                    )
            )
            .andExpect(status().isBadRequest())
            .andExpect(
                jsonPath("$.code")
                    .value(
                        "PAYMENT_REQUEST_MALFORMED"
                    )
            );

        assertThat(countRows("payment"))
            .isZero();
    }

    @Test
    void operationsOnlyUserCannotSubmitPayment()
        throws Exception {
        UserSession operations =
            createSession(
                "payment-operations@example.com",
                "OPERATIONS"
            );

        PaymentAccounts accounts =
            createPaymentAccounts(
                1_000L,
                100L
            );

        submit(
            operations.cookie(),
            "http-payment-forbidden",
            accounts,
            100L,
            true
        )
            .andExpect(status().isForbidden())
            .andExpect(
                jsonPath("$.code")
                    .value("SECURITY_ACCESS_DENIED")
            );

        assertThat(countRows("payment"))
            .isZero();
    }

    @Test
    void mutatingRequestRequiresCsrf()
        throws Exception {
        UserSession customer =
            createSession(
                "payment-csrf@example.com",
                null
            );

        PaymentAccounts accounts =
            createOwnedPaymentAccounts(
                customer.userId(),
                1_000L,
                100L
            );

        submit(
            customer.cookie(),
            "http-payment-csrf",
            accounts,
            100L,
            false
        )
            .andExpect(status().isForbidden())
            .andExpect(
                jsonPath("$.code")
                    .value("SECURITY_ACCESS_DENIED")
            );

        assertThat(countRows("payment"))
            .isZero();
    }

    @Test
    void anonymousUserCannotSubmitPayment()
        throws Exception {
        mockMvc.perform(
                post(PAYMENT_ENDPOINT)
                    .with(csrf())
                    .header(
                        PaymentIdempotencyHeader.NAME,
                        "http-payment-anonymous"
                    )
                    .contentType(
                        MediaType.APPLICATION_JSON
                    )
                    .content(
                        """
                        {
                          "sourceAccountId":
                            "10000000-0000-0000-0000-000000000001",
                          "destinationAccountId":
                            "20000000-0000-0000-0000-000000000001",
                          "amountMinorUnits": 100
                        }
                        """
                    )
            )
            .andExpect(status().isUnauthorized())
            .andExpect(
                jsonPath("$.code")
                    .value(
                        "SECURITY_AUTHENTICATION_REQUIRED"
                    )
            );

        assertThat(countRows("payment"))
            .isZero();
    }

    @Test
    void openApiDocumentsPaymentSubmission()
        throws Exception {
        mockMvc.perform(
                get("/v3/api-docs")
            )
            .andExpect(status().isOk())
            .andExpect(
                jsonPath(
                    "$.paths['/api/v1/payments'].post"
                )
                    .exists()
            )
            .andExpect(
                jsonPath(
                    "$.paths['/api/v1/payments']"
                        + ".post.parameters[0].name"
                )
                    .value(
                        PaymentIdempotencyHeader.NAME
                    )
            )
            .andExpect(
                jsonPath(
                    "$.paths['/api/v1/payments']"
                        + ".post.parameters[0].required"
                )
                    .value(true)
            );
    }

    private ResultActions submit(
        Cookie session,
        String idempotencyKey,
        PaymentAccounts accounts,
        long amountMinorUnits,
        boolean includeCsrf
    ) throws Exception {
        var request =
            post(PAYMENT_ENDPOINT)
                .cookie(session)
                .contentType(
                    MediaType.APPLICATION_JSON
                )
                .content(
                    """
                    {
                      "sourceAccountId": "%s",
                      "destinationAccountId": "%s",
                      "amountMinorUnits": %d
                    }
                    """
                        .formatted(
                            accounts.sourceAccountId(),
                            accounts.destinationAccountId(),
                            amountMinorUnits
                        )
                );

        if (includeCsrf) {
            request.with(csrf());
        }

        if (idempotencyKey != null) {
            request.header(
                PaymentIdempotencyHeader.NAME,
                idempotencyKey
            );
        }

        return mockMvc.perform(request);
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

    private PaymentAccounts
    createOwnedPaymentAccounts(
        UUID identityUserId,
        long sourceBalance,
        long destinationBalance
    ) {
        UUID sourceCustomerId =
            insertCustomer("Payment Source Customer");

        UUID destinationCustomerId =
            insertCustomer(
                "Payment Destination Customer"
            );

        jdbcTemplate.update(
            """
            INSERT INTO customer_identity_assignment (
                identity_user_id,
                customer_id,
                assigned_at,
                version
            )
            VALUES (?, ?, ?, ?)
            """,
            identityUserId,
            sourceCustomerId,
            FIXTURE_TIME.atOffset(
                ZoneOffset.UTC
            ),
            0L
        );

        return new PaymentAccounts(
            insertAccount(
                sourceCustomerId,
                sourceBalance
            ),
            insertAccount(
                destinationCustomerId,
                destinationBalance
            )
        );
    }

    private PaymentAccounts createPaymentAccounts(
        long sourceBalance,
        long destinationBalance
    ) {
        UUID sourceCustomerId =
            insertCustomer("Unowned Source Customer");

        UUID destinationCustomerId =
            insertCustomer(
                "Unowned Destination Customer"
            );

        return new PaymentAccounts(
            insertAccount(
                sourceCustomerId,
                sourceBalance
            ),
            insertAccount(
                destinationCustomerId,
                destinationBalance
            )
        );
    }

    private UUID insertCustomer(
        String fullName
    ) {
        UUID customerId =
            UUID.randomUUID();

        jdbcTemplate.update(
            """
            INSERT INTO customer_profile (
                id,
                full_name,
                status,
                created_at,
                updated_at,
                version
            )
            VALUES (?, ?, ?, ?, ?, ?)
            """,
            customerId,
            fullName,
            "ACTIVE",
            FIXTURE_TIME.atOffset(
                ZoneOffset.UTC
            ),
            FIXTURE_TIME.atOffset(
                ZoneOffset.UTC
            ),
            0L
        );

        return customerId;
    }

    private UUID insertAccount(
        UUID customerId,
        long balanceMinorUnits
    ) {
        UUID accountId =
            UUID.randomUUID();

        jdbcTemplate.update(
            """
            INSERT INTO customer_account (
                id,
                customer_id,
                currency,
                balance_minor_units,
                status,
                created_at,
                updated_at,
                version
            )
            VALUES (?, ?, ?, ?, ?, ?, ?, ?)
            """,
            accountId,
            customerId,
            "GBP",
            balanceMinorUnits,
            "ACTIVE",
            FIXTURE_TIME.atOffset(
                ZoneOffset.UTC
            ),
            FIXTURE_TIME.atOffset(
                ZoneOffset.UTC
            ),
            0L
        );

        return accountId;
    }

    private long accountBalance(
        UUID accountId
    ) {
        Long balance =
            jdbcTemplate.queryForObject(
                """
                SELECT balance_minor_units
                FROM customer_account
                WHERE id = ?
                """,
                Long.class,
                accountId
            );

        return Objects.requireNonNull(balance);
    }

    private long countRows(
        String tableName
    ) {
        Long count =
            jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM " + tableName,
                Long.class
            );

        return count == null ? 0L : count;
    }

    private record UserSession(
        UUID userId,
        Cookie cookie
    ) {
    }

    private record PaymentAccounts(
        UUID sourceAccountId,
        UUID destinationAccountId
    ) {
    }
}
