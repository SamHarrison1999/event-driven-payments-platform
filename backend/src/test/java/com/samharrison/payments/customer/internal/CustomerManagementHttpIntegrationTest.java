package com.samharrison.payments.customer.internal;

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
class CustomerManagementHttpIntegrationTest {

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
                "payments_customer_http_test"
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
    void operationsUserManagesCustomerThroughHttp()
        throws Exception {
        Cookie operationsSession =
            createSession(
                "operations@example.com",
                "OPERATIONS"
            );

        MvcResult createResult =
            mockMvc.perform(
                    post(CUSTOMER_ENDPOINT)
                        .cookie(operationsSession)
                        .with(csrf())
                        .contentType(
                            MediaType.APPLICATION_JSON
                        )
                        .content(
                            """
                            {
                              "fullName": "  Sam Example  "
                            }
                            """
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
                            CUSTOMER_ENDPOINT + "/"
                        )
                    )
                )
                .andExpect(
                    jsonPath("$.fullName")
                        .value("Sam Example")
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

        UUID customerId =
            UUID.fromString(
                location.substring(
                    location.lastIndexOf('/') + 1
                )
            );

        mockMvc.perform(
                get(
                    CUSTOMER_ENDPOINT
                        + "/"
                        + customerId
                )
                    .cookie(operationsSession)
            )
            .andExpect(status().isOk())
            .andExpect(
                jsonPath("$.id")
                    .value(customerId.toString())
            )
            .andExpect(
                jsonPath("$.fullName")
                    .value("Sam Example")
            );

        mockMvc.perform(
                put(
                    CUSTOMER_ENDPOINT
                        + "/"
                        + customerId
                        + "/name"
                )
                    .cookie(operationsSession)
                    .with(csrf())
                    .header(
                        HttpHeaders.IF_MATCH,
                        CustomerVersionPrecondition.format(
                            customerVersion(customerId)
                        )
                    )
                    .contentType(
                        MediaType.APPLICATION_JSON
                    )
                    .content(
                        """
                        {
                          "fullName": "Samuel Example"
                        }
                        """
                    )
            )
            .andExpect(status().isOk())
            .andExpect(
                jsonPath("$.fullName")
                    .value("Samuel Example")
            )
            .andExpect(
                jsonPath("$.version")
                    .value(1)
            );

        updateStatus(
            operationsSession,
            customerId,
            "SUSPENDED"
        )
            .andExpect(status().isOk())
            .andExpect(
                jsonPath("$.status")
                    .value("SUSPENDED")
            )
            .andExpect(
                jsonPath("$.version")
                    .value(2)
            );

        updateStatus(
            operationsSession,
            customerId,
            "ACTIVE"
        )
            .andExpect(status().isOk())
            .andExpect(
                jsonPath("$.status")
                    .value("ACTIVE")
            )
            .andExpect(
                jsonPath("$.version")
                    .value(3)
            );

        updateStatus(
            operationsSession,
            customerId,
            "CLOSED"
        )
            .andExpect(status().isOk())
            .andExpect(
                jsonPath("$.status")
                    .value("CLOSED")
            )
            .andExpect(
                jsonPath("$.version")
                    .value(4)
            );

        mockMvc.perform(
                put(
                    CUSTOMER_ENDPOINT
                        + "/"
                        + customerId
                        + "/name"
                )
                    .cookie(operationsSession)
                    .with(csrf())
                    .header(
                        HttpHeaders.IF_MATCH,
                        CustomerVersionPrecondition.format(
                            customerVersion(customerId)
                        )
                    )
                    .contentType(
                        MediaType.APPLICATION_JSON
                    )
                    .content(
                        """
                        {
                          "fullName": "Closed Customer"
                        }
                        """
                    )
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
                        "CUSTOMER_LIFECYCLE_CONFLICT"
                    )
            );
    }

    @Test
    void administratorCanCreateCustomer()
        throws Exception {
        Cookie administratorSession =
            createSession(
                "administrator@example.com",
                "ADMIN"
            );

        mockMvc.perform(
                post(CUSTOMER_ENDPOINT)
                    .cookie(administratorSession)
                    .with(csrf())
                    .contentType(
                        MediaType.APPLICATION_JSON
                    )
                    .content(
                        """
                        {
                          "fullName": "Admin Created"
                        }
                        """
                    )
            )
            .andExpect(status().isCreated())
            .andExpect(
                jsonPath("$.fullName")
                    .value("Admin Created")
            );
    }

    @Test
    void customerUserCannotCreateCustomer()
        throws Exception {
        Cookie customerSession =
            createSession(
                "customer@example.com",
                null
            );

        mockMvc.perform(
                post(CUSTOMER_ENDPOINT)
                    .cookie(customerSession)
                    .with(csrf())
                    .contentType(
                        MediaType.APPLICATION_JSON
                    )
                    .content(
                        """
                        {
                          "fullName": "Forbidden Customer"
                        }
                        """
                    )
            )
            .andExpect(status().isForbidden());

        assertThat(
            customerCount()
        )
            .isZero();
    }

    @Test
    void anonymousUserCannotReadCustomer()
        throws Exception {
        mockMvc.perform(
                get(
                    CUSTOMER_ENDPOINT
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

        mockMvc.perform(
                post(CUSTOMER_ENDPOINT)
                    .cookie(operationsSession)
                    .contentType(
                        MediaType.APPLICATION_JSON
                    )
                    .content(
                        """
                        {
                          "fullName": "No Csrf Customer"
                        }
                        """
                    )
            )
            .andExpect(status().isForbidden());

        assertThat(
            customerCount()
        )
            .isZero();
    }

    @Test
    void invalidNameReturnsProblemDetails()
        throws Exception {
        Cookie operationsSession =
            createSession(
                "validation@example.com",
                "OPERATIONS"
            );

        mockMvc.perform(
                post(CUSTOMER_ENDPOINT)
                    .cookie(operationsSession)
                    .with(csrf())
                    .contentType(
                        MediaType.APPLICATION_JSON
                    )
                    .content(
                        """
                        {
                          "fullName": "   "
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
                        "CUSTOMER_REQUEST_INVALID"
                    )
            )
            .andExpect(
                jsonPath(
                    "$.violations[0].field"
                )
                    .value("fullName")
            );
    }

    @Test
    void missingCustomerReturnsProblemDetails()
        throws Exception {
        Cookie operationsSession =
            createSession(
                "lookup@example.com",
                "OPERATIONS"
            );

        UUID missingId =
            UUID.randomUUID();

        mockMvc.perform(
                get(
                    CUSTOMER_ENDPOINT
                        + "/"
                        + missingId
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
                    .value("CUSTOMER_NOT_FOUND")
            )
            .andExpect(
                jsonPath("$.detail")
                    .value(
                        containsString(
                            missingId.toString()
                        )
                    )
            );
    }

    @Test
    void unknownStatusReturnsMalformedProblem()
        throws Exception {
        Cookie operationsSession =
            createSession(
                "status@example.com",
                "OPERATIONS"
            );

        UUID customerId =
            createCustomer(
                operationsSession,
                "Status Customer"
            );

        mockMvc.perform(
                put(
                    CUSTOMER_ENDPOINT
                        + "/"
                        + customerId
                        + "/status"
                )
                    .cookie(operationsSession)
                    .with(csrf())
                    .header(
                        HttpHeaders.IF_MATCH,
                        CustomerVersionPrecondition.format(
                            customerVersion(customerId)
                        )
                    )
                    .contentType(
                        MediaType.APPLICATION_JSON
                    )
                    .content(
                        """
                        {
                          "status": "UNKNOWN"
                        }
                        """
                    )
            )
            .andExpect(status().isBadRequest())
            .andExpect(
                jsonPath("$.code")
                    .value(
                        "CUSTOMER_REQUEST_MALFORMED"
                    )
            );
    }

    private UUID createCustomer(
        Cookie session,
        String fullName
    ) throws Exception {
        MvcResult result =
            mockMvc.perform(
                    post(CUSTOMER_ENDPOINT)
                        .cookie(session)
                        .with(csrf())
                        .contentType(
                            MediaType.APPLICATION_JSON
                        )
                        .content(
                            """
                            {
                              "fullName": "%s"
                            }
                            """
                                .formatted(fullName)
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
        UUID customerId,
        String statusValue
    ) throws Exception {
        return mockMvc.perform(
            put(
                CUSTOMER_ENDPOINT
                    + "/"
                    + customerId
                    + "/status"
            )
                .cookie(session)
                .with(csrf())
                .header(
                    HttpHeaders.IF_MATCH,
                    CustomerVersionPrecondition.format(
                        customerVersion(customerId)
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

    private long customerVersion(
        UUID customerId
    ) {
        Long version =
            jdbcTemplate.queryForObject(
                """
                SELECT version
                FROM customer_profile
                WHERE id = ?
                """,
                Long.class,
                customerId
            );

        if (version == null) {
            throw new IllegalStateException(
                "Customer version was not found."
            );
        }

        return version;
    }
    private long customerCount() {
        Long count =
            jdbcTemplate.queryForObject(
                """
                SELECT COUNT(*)
                FROM customer_profile
                """,
                Long.class
            );

        return count == null ? 0L : count;
    }
}