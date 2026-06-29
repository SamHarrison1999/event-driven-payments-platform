package com.samharrison.payments.account.internal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
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
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
@DirtiesContext(
    classMode = DirtiesContext.ClassMode.AFTER_CLASS
)
class CustomerAccountOwnershipHttpIntegrationTest {

    private static final String ACCOUNT_ENDPOINT =
        "/api/v1/accounts";

    private static final String CUSTOMER_ENDPOINT =
        "/api/v1/customers";

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
                "payments_account_ownership_http_test"
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
    void customerUserViewsOnlyOwnedAccounts()
        throws Exception {
        UserSession customer =
            createSession(
                "owned-customer@example.com",
                null
            );

        UserSession operations =
            createSession(
                "ownership-operations@example.com",
                "OPERATIONS"
            );

        UUID ownedCustomerId =
            insertCustomer(
                "Owned Customer",
                "ACTIVE"
            );

        UUID otherCustomerId =
            insertCustomer(
                "Other Customer",
                "ACTIVE"
            );

        assignOwnership(
            operations.cookie(),
            ownedCustomerId,
            customer.userId()
        )
            .andExpect(status().isOk())
            .andExpect(
                header().string(
                    HttpHeaders.CACHE_CONTROL,
                    containsString("no-store")
                )
            )
            .andExpect(
                jsonPath("$.identityUserId")
                    .value(
                        customer.userId().toString()
                    )
            )
            .andExpect(
                jsonPath("$.customerId")
                    .value(
                        ownedCustomerId.toString()
                    )
            )
            .andExpect(
                jsonPath("$.version")
                    .value(0)
            );

        UUID firstAccountId =
            insertAccount(
                ownedCustomerId,
                1250L,
                "ACTIVE",
                "2026-06-29T09:00:00Z"
            );

        UUID secondAccountId =
            insertAccount(
                ownedCustomerId,
                5000L,
                "FROZEN",
                "2026-06-29T10:00:00Z"
            );

        insertAccount(
            otherCustomerId,
            9999L,
            "ACTIVE",
            "2026-06-29T08:00:00Z"
        );

        mockMvc.perform(
                get(ACCOUNT_ENDPOINT)
                    .cookie(customer.cookie())
            )
            .andExpect(status().isOk())
            .andExpect(
                header().string(
                    HttpHeaders.CACHE_CONTROL,
                    containsString("no-store")
                )
            )
            .andExpect(
                jsonPath("$", hasSize(2))
            )
            .andExpect(
                jsonPath("$[0].id")
                    .value(firstAccountId.toString())
            )
            .andExpect(
                jsonPath("$[0].customerId")
                    .value(
                        ownedCustomerId.toString()
                    )
            )
            .andExpect(
                jsonPath("$[0].currency")
                    .value("GBP")
            )
            .andExpect(
                jsonPath("$[0].balanceMinorUnits")
                    .value(1250)
            )
            .andExpect(
                jsonPath("$[0].status")
                    .value("ACTIVE")
            )
            .andExpect(
                jsonPath("$[1].id")
                    .value(secondAccountId.toString())
            )
            .andExpect(
                jsonPath("$[1].status")
                    .value("FROZEN")
            );
    }

    @Test
    void operationsUserViewsAccountsForCustomer()
        throws Exception {
        UserSession operations =
            createSession(
                "query-operations@example.com",
                "OPERATIONS"
            );

        UUID customerId =
            insertCustomer(
                "Operations Query Customer",
                "ACTIVE"
            );

        UUID accountId =
            insertAccount(
                customerId,
                300L,
                "ACTIVE",
                "2026-06-29T09:00:00Z"
            );

        mockMvc.perform(
                get(
                    CUSTOMER_ENDPOINT
                        + "/"
                        + customerId
                        + "/accounts"
                )
                    .cookie(operations.cookie())
            )
            .andExpect(status().isOk())
            .andExpect(
                jsonPath("$", hasSize(1))
            )
            .andExpect(
                jsonPath("$[0].id")
                    .value(accountId.toString())
            );
    }

    @Test
    void customerCannotQueryArbitraryCustomer()
        throws Exception {
        UserSession customer =
            createSession(
                "restricted-customer@example.com",
                null
            );

        mockMvc.perform(
                get(
                    CUSTOMER_ENDPOINT
                        + "/"
                        + UUID.randomUUID()
                        + "/accounts"
                )
                    .cookie(customer.cookie())
            )
            .andExpect(status().isForbidden());
    }

    @Test
    void anonymousUserCannotQueryOwnedAccounts()
        throws Exception {
        mockMvc.perform(
                get(ACCOUNT_ENDPOINT)
            )
            .andExpect(status().isUnauthorized());
    }

    @Test
    void missingOwnershipReturnsProblem()
        throws Exception {
        UserSession customer =
            createSession(
                "unassigned-customer@example.com",
                null
            );

        mockMvc.perform(
                get(ACCOUNT_ENDPOINT)
                    .cookie(customer.cookie())
            )
            .andExpect(status().isNotFound())
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
                        "CUSTOMER_OWNERSHIP_NOT_FOUND"
                    )
            )
            .andExpect(
                jsonPath("$.detail")
                    .value(
                        containsString(
                            customer.userId().toString()
                        )
                    )
            );
    }

    @Test
    void ownershipAssignmentRequiresCsrf()
        throws Exception {
        UserSession operations =
            createSession(
                "csrf-ownership@example.com",
                "OPERATIONS"
            );

        UserSession customer =
            createSession(
                "csrf-target@example.com",
                null
            );

        UUID customerId =
            insertCustomer(
                "CSRF Customer",
                "ACTIVE"
            );

        mockMvc.perform(
                put(
                    ownershipEndpoint(
                        customerId,
                        customer.userId()
                    )
                )
                    .cookie(operations.cookie())
            )
            .andExpect(status().isForbidden());

        assertThat(ownershipCount())
            .isZero();
    }

    @Test
    void customerUserCannotAssignOwnership()
        throws Exception {
        UserSession customer =
            createSession(
                "forbidden-owner@example.com",
                null
            );

        UUID customerId =
            insertCustomer(
                "Forbidden Ownership Customer",
                "ACTIVE"
            );

        assignOwnership(
            customer.cookie(),
            customerId,
            customer.userId()
        )
            .andExpect(status().isForbidden());

        assertThat(ownershipCount())
            .isZero();
    }

    @Test
    void missingIdentityReturnsNotFoundProblem()
        throws Exception {
        UserSession operations =
            createSession(
                "missing-identity@example.com",
                "OPERATIONS"
            );

        UUID customerId =
            insertCustomer(
                "Missing Identity Customer",
                "ACTIVE"
            );

        UUID missingIdentityUserId =
            UUID.randomUUID();

        assignOwnership(
            operations.cookie(),
            customerId,
            missingIdentityUserId
        )
            .andExpect(status().isNotFound())
            .andExpect(
                content().contentTypeCompatibleWith(
                    MediaType.APPLICATION_PROBLEM_JSON
                )
            )
            .andExpect(
                jsonPath("$.code")
                    .value(
                        "IDENTITY_USER_NOT_FOUND"
                    )
            );
    }

    @Test
    void inactiveCustomerReturnsConflictProblem()
        throws Exception {
        UserSession operations =
            createSession(
                "inactive-ownership@example.com",
                "OPERATIONS"
            );

        UserSession customer =
            createSession(
                "inactive-target@example.com",
                null
            );

        UUID customerId =
            insertCustomer(
                "Inactive Ownership Customer",
                "SUSPENDED"
            );

        assignOwnership(
            operations.cookie(),
            customerId,
            customer.userId()
        )
            .andExpect(status().isConflict())
            .andExpect(
                jsonPath("$.code")
                    .value(
                        "CUSTOMER_OWNERSHIP_INELIGIBLE"
                    )
            );

        assertThat(ownershipCount())
            .isZero();
    }

    @Test
    void identityCannotBeAssignedToTwoCustomers()
        throws Exception {
        UserSession operations =
            createSession(
                "conflict-operations@example.com",
                "OPERATIONS"
            );

        UserSession customer =
            createSession(
                "conflict-target@example.com",
                null
            );

        UUID firstCustomerId =
            insertCustomer(
                "First Ownership Customer",
                "ACTIVE"
            );

        UUID secondCustomerId =
            insertCustomer(
                "Second Ownership Customer",
                "ACTIVE"
            );

        assignOwnership(
            operations.cookie(),
            firstCustomerId,
            customer.userId()
        )
            .andExpect(status().isOk());

        assignOwnership(
            operations.cookie(),
            secondCustomerId,
            customer.userId()
        )
            .andExpect(status().isConflict())
            .andExpect(
                jsonPath("$.code")
                    .value(
                        "CUSTOMER_OWNERSHIP_CONFLICT"
                    )
            );

        assertThat(ownershipCount())
            .isEqualTo(1L);
    }

    @Test
    void customerCanHaveMultipleIdentityUsers()
        throws Exception {
        UserSession operations =
            createSession(
                "shared-operations@example.com",
                "OPERATIONS"
            );

        UserSession firstCustomerUser =
            createSession(
                "shared-first@example.com",
                null
            );

        UserSession secondCustomerUser =
            createSession(
                "shared-second@example.com",
                null
            );

        UUID customerId =
            insertCustomer(
                "Shared Ownership Customer",
                "ACTIVE"
            );

        assignOwnership(
            operations.cookie(),
            customerId,
            firstCustomerUser.userId()
        )
            .andExpect(status().isOk());

        assignOwnership(
            operations.cookie(),
            customerId,
            secondCustomerUser.userId()
        )
            .andExpect(status().isOk());

        assertThat(ownershipCount())
            .isEqualTo(2L);
    }

    private org.springframework.test.web.servlet.ResultActions
    assignOwnership(
        Cookie session,
        UUID customerId,
        UUID identityUserId
    ) throws Exception {
        return mockMvc.perform(
            put(
                ownershipEndpoint(
                    customerId,
                    identityUserId
                )
            )
                .cookie(session)
                .with(csrf())
        );
    }

    private static String ownershipEndpoint(
        UUID customerId,
        UUID identityUserId
    ) {
        return CUSTOMER_ENDPOINT
            + "/"
            + customerId
            + "/identity-users/"
            + identityUserId;
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

        assertThat(userId)
            .isNotNull();

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

        assertThat(sessionCookie)
            .isNotNull();

        return sessionCookie;
    }

    private UUID insertCustomer(
        String fullName,
        String status
    ) {
        UUID customerId =
            UUID.randomUUID();

        Instant timestamp =
            Instant.parse(
                "2026-06-29T08:00:00Z"
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
            fullName,
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

    private UUID insertAccount(
        UUID customerId,
        long balanceMinorUnits,
        String status,
        String createdAt
    ) {
        UUID accountId =
            UUID.randomUUID();

        Instant timestamp =
            Instant.parse(createdAt);

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
            status,
            timestamp.atOffset(
                ZoneOffset.UTC
            ),
            timestamp.atOffset(
                ZoneOffset.UTC
            ),
            0L
        );

        return accountId;
    }

    private long ownershipCount() {
        Long count =
            jdbcTemplate.queryForObject(
                """
                SELECT COUNT(*)
                FROM customer_identity_assignment
                """,
                Long.class
            );

        return count == null ? 0L : count;
    }

    private record UserSession(
        UUID userId,
        Cookie cookie
    ) {
    }
}