package com.samharrison.payments.account.internal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
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
class AccountManagementHttpIntegrationTest {

    private static final String ACCOUNT_ENDPOINT =
        "/api/v1/accounts";

    private static final String REGISTRATION_ENDPOINT =
        "/api/v1/identity/registrations";

    private static final String SESSION_ENDPOINT =
        "/api/v1/identity/session";

    private static final String SESSION_COOKIE_NAME =
        "PAYMENTS_SESSION";

    private static final String PASSWORD =
        "this is a secure customer passphrase";

    @Container
    @ServiceConnection
    static final PostgreSQLContainer POSTGRESQL =
        new PostgreSQLContainer(
            "postgres:18.4-alpine"
        )
            .withDatabaseName(
                "payments_account_http_test"
            )
            .withUsername("payments_test")
            .withPassword("payments_test_only");

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void clearStoredData() {
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
    void operationsUserManagesAccountThroughHttp()
        throws Exception {
        Cookie operationsSession =
            createSession(
                "operations@example.com",
                "OPERATIONS"
            );

        UUID customerId =
            insertCustomer("ACTIVE");

        MvcResult createResult =
            mockMvc.perform(
                    post(ACCOUNT_ENDPOINT)
                        .cookie(operationsSession)
                        .with(csrf())
                        .contentType(
                            MediaType.APPLICATION_JSON
                        )
                        .content(
                            """
                            {
                              "customerId": "%s"
                            }
                            """
                                .formatted(customerId)
                        )
                )
                .andExpect(status().isCreated())
                .andExpect(
                    header().string(
                        HttpHeaders.CACHE_CONTROL,
                        containsString("no-store")
                    )
                )
                .andExpect(
                    header().string(
                        HttpHeaders.LOCATION,
                        containsString(
                            ACCOUNT_ENDPOINT + "/"
                        )
                    )
                )
                .andExpect(
                    jsonPath("$.customerId")
                        .value(customerId.toString())
                )
                .andExpect(
                    jsonPath("$.currency")
                        .value("GBP")
                )
                .andExpect(
                    jsonPath("$.balanceMinorUnits")
                        .value(0)
                )
                .andExpect(
                    jsonPath("$.status")
                        .value("ACTIVE")
                )
                .andExpect(
                    jsonPath("$.version")
                        .value(0)
                )
                .andReturn();

        String location =
            createResult
                .getResponse()
                .getHeader(
                    HttpHeaders.LOCATION
                );

        assertThat(location)
            .isNotNull();

        UUID accountId =
            UUID.fromString(
                location.substring(
                    location.lastIndexOf('/') + 1
                )
            );

        mockMvc.perform(
                get(
                    ACCOUNT_ENDPOINT
                        + "/"
                        + accountId
                )
                    .cookie(operationsSession)
            )
            .andExpect(status().isOk())
            .andExpect(
                jsonPath("$.id")
                    .value(accountId.toString())
            )
            .andExpect(
                jsonPath("$.customerId")
                    .value(customerId.toString())
            );

        updateStatus(
            operationsSession,
            accountId,
            "FROZEN"
        )
            .andExpect(status().isOk())
            .andExpect(
                jsonPath("$.status")
                    .value("FROZEN")
            )
            .andExpect(
                jsonPath("$.version")
                    .value(1)
            );

        updateStatus(
            operationsSession,
            accountId,
            "ACTIVE"
        )
            .andExpect(status().isOk())
            .andExpect(
                jsonPath("$.status")
                    .value("ACTIVE")
            )
            .andExpect(
                jsonPath("$.version")
                    .value(2)
            );

        updateStatus(
            operationsSession,
            accountId,
            "CLOSED"
        )
            .andExpect(status().isOk())
            .andExpect(
                jsonPath("$.status")
                    .value("CLOSED")
            )
            .andExpect(
                jsonPath("$.version")
                    .value(3)
            );

        updateStatus(
            operationsSession,
            accountId,
            "ACTIVE"
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
                        "ACCOUNT_LIFECYCLE_CONFLICT"
                    )
            );
    }

    @Test
    void administratorCanCreateAccount()
        throws Exception {
        Cookie administratorSession =
            createSession(
                "administrator@example.com",
                "ADMIN"
            );

        UUID customerId =
            insertCustomer("ACTIVE");

        createAccount(
            administratorSession,
            customerId
        );

        assertThat(accountCount())
            .isEqualTo(1L);
    }

    @Test
    void customerUserCannotCreateAccount()
        throws Exception {
        Cookie customerSession =
            createSession(
                "customer@example.com",
                null
            );

        UUID customerId =
            insertCustomer("ACTIVE");

        mockMvc.perform(
                post(ACCOUNT_ENDPOINT)
                    .cookie(customerSession)
                    .with(csrf())
                    .contentType(
                        MediaType.APPLICATION_JSON
                    )
                    .content(
                        """
                        {
                          "customerId": "%s"
                        }
                        """
                            .formatted(customerId)
                    )
            )
            .andExpect(status().isForbidden());

        assertThat(accountCount())
            .isZero();
    }

    @Test
    void anonymousUserCannotReadAccount()
        throws Exception {
        mockMvc.perform(
                get(
                    ACCOUNT_ENDPOINT
                        + "/"
                        + UUID.randomUUID()
                )
            )
            .andExpect(status().isUnauthorized());
    }

    @Test
    void mutatingRequestRequiresCsrf()
        throws Exception {
        Cookie operationsSession =
            createSession(
                "csrf-operations@example.com",
                "OPERATIONS"
            );

        UUID customerId =
            insertCustomer("ACTIVE");

        mockMvc.perform(
                post(ACCOUNT_ENDPOINT)
                    .cookie(operationsSession)
                    .contentType(
                        MediaType.APPLICATION_JSON
                    )
                    .content(
                        """
                        {
                          "customerId": "%s"
                        }
                        """
                            .formatted(customerId)
                    )
            )
            .andExpect(status().isForbidden());

        assertThat(accountCount())
            .isZero();
    }

    @Test
    void missingCustomerIdReturnsValidationProblem()
        throws Exception {
        Cookie operationsSession =
            createSession(
                "validation@example.com",
                "OPERATIONS"
            );

        mockMvc.perform(
                post(ACCOUNT_ENDPOINT)
                    .cookie(operationsSession)
                    .with(csrf())
                    .contentType(
                        MediaType.APPLICATION_JSON
                    )
                    .content(
                        """
                        {
                          "customerId": null
                        }
                        """
                    )
            )
            .andExpect(status().isBadRequest())
            .andExpect(
                content().contentTypeCompatibleWith(
                    MediaType.APPLICATION_PROBLEM_JSON
                )
            )
            .andExpect(
                header().string(
                    HttpHeaders.CACHE_CONTROL,
                    containsString("no-store")
                )
            )
            .andExpect(
                jsonPath("$.code")
                    .value(
                        "ACCOUNT_REQUEST_INVALID"
                    )
            )
            .andExpect(
                jsonPath(
                    "$.violations[0].field"
                )
                    .value("customerId")
            );
    }

    @Test
    void missingCustomerReturnsNotFoundProblem()
        throws Exception {
        Cookie operationsSession =
            createSession(
                "missing-customer@example.com",
                "OPERATIONS"
            );

        UUID missingCustomerId =
            UUID.randomUUID();

        mockMvc.perform(
                post(ACCOUNT_ENDPOINT)
                    .cookie(operationsSession)
                    .with(csrf())
                    .contentType(
                        MediaType.APPLICATION_JSON
                    )
                    .content(
                        """
                        {
                          "customerId": "%s"
                        }
                        """
                            .formatted(
                                missingCustomerId
                            )
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
                    .value("CUSTOMER_NOT_FOUND")
            )
            .andExpect(
                jsonPath("$.detail")
                    .value(
                        containsString(
                            missingCustomerId.toString()
                        )
                    )
            );
    }

    @Test
    void inactiveCustomerReturnsConflictProblem()
        throws Exception {
        Cookie operationsSession =
            createSession(
                "inactive-customer@example.com",
                "OPERATIONS"
            );

        UUID customerId =
            insertCustomer("SUSPENDED");

        mockMvc.perform(
                post(ACCOUNT_ENDPOINT)
                    .cookie(operationsSession)
                    .with(csrf())
                    .contentType(
                        MediaType.APPLICATION_JSON
                    )
                    .content(
                        """
                        {
                          "customerId": "%s"
                        }
                        """
                            .formatted(customerId)
                    )
            )
            .andExpect(status().isConflict())
            .andExpect(
                jsonPath("$.code")
                    .value(
                        "CUSTOMER_ACCOUNT_INELIGIBLE"
                    )
            );

        assertThat(accountCount())
            .isZero();
    }

    @Test
    void missingAccountReturnsNotFoundProblem()
        throws Exception {
        Cookie operationsSession =
            createSession(
                "missing-account@example.com",
                "OPERATIONS"
            );

        UUID missingAccountId =
            UUID.randomUUID();

        mockMvc.perform(
                get(
                    ACCOUNT_ENDPOINT
                        + "/"
                        + missingAccountId
                )
                    .cookie(operationsSession)
            )
            .andExpect(status().isNotFound())
            .andExpect(
                content().contentTypeCompatibleWith(
                    MediaType.APPLICATION_PROBLEM_JSON
                )
            )
            .andExpect(
                jsonPath("$.code")
                    .value("ACCOUNT_NOT_FOUND")
            )
            .andExpect(
                jsonPath("$.detail")
                    .value(
                        containsString(
                            missingAccountId.toString()
                        )
                    )
            );
    }

    @Test
    void unknownStatusReturnsMalformedProblem()
        throws Exception {
        Cookie operationsSession =
            createSession(
                "unknown-status@example.com",
                "OPERATIONS"
            );

        UUID accountId =
            createAccount(
                operationsSession,
                insertCustomer("ACTIVE")
            );

        updateStatus(
            operationsSession,
            accountId,
            "UNKNOWN"
        )
            .andExpect(status().isBadRequest())
            .andExpect(
                jsonPath("$.code")
                    .value(
                        "ACCOUNT_REQUEST_MALFORMED"
                    )
            );
    }

    @Test
    void fundedAccountCannotBeClosed()
        throws Exception {
        Cookie operationsSession =
            createSession(
                "funded-account@example.com",
                "OPERATIONS"
            );

        UUID accountId =
            createAccount(
                operationsSession,
                insertCustomer("ACTIVE")
            );

        jdbcTemplate.update(
            """
            UPDATE customer_account
            SET balance_minor_units = ?
            WHERE id = ?
            """,
            100L,
            accountId
        );

        updateStatus(
            operationsSession,
            accountId,
            "CLOSED"
        )
            .andExpect(status().isConflict())
            .andExpect(
                jsonPath("$.code")
                    .value(
                        "ACCOUNT_LIFECYCLE_CONFLICT"
                    )
            );

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

        assertThat(balance)
            .isEqualTo(100L);
    }

    @Test
    void unknownAccountPropertyReturnsMalformedProblem()
        throws Exception {
        Cookie operationsSession =
            createSession(
                "unknown-account-field@example.com",
                "OPERATIONS"
            );

        UUID customerId =
            insertCustomer("ACTIVE");

        mockMvc.perform(
                post(ACCOUNT_ENDPOINT)
                    .cookie(operationsSession)
                    .with(csrf())
                    .contentType(
                        MediaType.APPLICATION_JSON
                    )
                    .content(
                        """
                        {
                          "customerId": "%s",
                          "unexpected": true
                        }
                        """
                            .formatted(customerId)
                    )
            )
            .andExpect(status().isBadRequest())
            .andExpect(
                content().contentTypeCompatibleWith(
                    MediaType.APPLICATION_PROBLEM_JSON
                )
            )
            .andExpect(
                header().string(
                    HttpHeaders.CACHE_CONTROL,
                    containsString("no-store")
                )
            )
            .andExpect(
                jsonPath("$.code")
                    .value(
                        "ACCOUNT_REQUEST_MALFORMED"
                    )
            );

        assertThat(accountCount())
            .isZero();
    }

    @Test
    void malformedAccountIdentifierReturnsProblem()
        throws Exception {
        Cookie operationsSession =
            createSession(
                "invalid-account-id@example.com",
                "OPERATIONS"
            );

        mockMvc.perform(
                get(
                    ACCOUNT_ENDPOINT
                        + "/not-a-uuid"
                )
                    .cookie(operationsSession)
            )
            .andExpect(status().isBadRequest())
            .andExpect(
                content().contentTypeCompatibleWith(
                    MediaType.APPLICATION_PROBLEM_JSON
                )
            )
            .andExpect(
                header().string(
                    HttpHeaders.CACHE_CONTROL,
                    containsString("no-store")
                )
            )
            .andExpect(
                jsonPath("$.code")
                    .value(
                        "ACCOUNT_IDENTIFIER_INVALID"
                    )
            );
    }
    private UUID createAccount(
        Cookie session,
        UUID customerId
    ) throws Exception {
        MvcResult result =
            mockMvc.perform(
                    post(ACCOUNT_ENDPOINT)
                        .cookie(session)
                        .with(csrf())
                        .contentType(
                            MediaType.APPLICATION_JSON
                        )
                        .content(
                            """
                            {
                              "customerId": "%s"
                            }
                            """
                                .formatted(customerId)
                        )
                )
                .andExpect(status().isCreated())
                .andReturn();

        String location =
            result
                .getResponse()
                .getHeader(
                    HttpHeaders.LOCATION
                );

        assertThat(location)
            .isNotNull();

        return UUID.fromString(
            location.substring(
                location.lastIndexOf('/') + 1
            )
        );
    }

    private ResultActions updateStatus(
        Cookie session,
        UUID accountId,
        String statusValue
    ) throws Exception {
        return mockMvc.perform(
            put(
                ACCOUNT_ENDPOINT
                    + "/"
                    + accountId
                    + "/status"
            )
                .cookie(session)
                .with(csrf())
                .header(
                    HttpHeaders.IF_MATCH,
                    AccountVersionPrecondition.format(
                        accountVersion(accountId)
                    )
                )
                .contentType(
                    MediaType.APPLICATION_JSON
                )
                .content(
                    """
                    {
                      "status": "%s"
                    }
                    """
                        .formatted(statusValue)
                )
        );
    }

    private Cookie createSession(
        String email,
        String additionalRole
    ) throws Exception {
        register(email);

        if (additionalRole != null) {
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

        return login(email);
    }

    private void register(String email)
        throws Exception {
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

    private Cookie login(String email)
        throws Exception {
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

    private UUID insertCustomer(
        String status
    ) {
        UUID customerId =
            UUID.randomUUID();

        Instant timestamp =
            Instant.parse(
                "2026-06-29T09:00:00Z"
            );

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
            "Account HTTP Customer",
            status,
            timestamp.atOffset(
                ZoneOffset.UTC
            ),
            timestamp.atOffset(
                ZoneOffset.UTC
            ),
            0L
        );

        return customerId;
    }

    private long accountVersion(
        UUID accountId
    ) {
        Long version =
            jdbcTemplate.queryForObject(
                """
                SELECT version
                FROM customer_account
                WHERE id = ?
                """,
                Long.class,
                accountId
            );

        if (version == null) {
            throw new IllegalStateException(
                "Account version was not found."
            );
        }

        return version;
    }
    private long accountCount() {
        Long count =
            jdbcTemplate.queryForObject(
                """
                SELECT COUNT(*)
                FROM customer_account
                """,
                Long.class
            );

        return count == null ? 0L : count;
    }
}