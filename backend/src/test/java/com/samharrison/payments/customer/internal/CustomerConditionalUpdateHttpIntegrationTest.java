package com.samharrison.payments.customer.internal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.samharrison.payments.identity.CurrentIdentityUser;
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
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
@WithMockUser(roles = "OPERATIONS")
@DirtiesContext(
    classMode = DirtiesContext.ClassMode.AFTER_CLASS
)
class CustomerConditionalUpdateHttpIntegrationTest {

    private static final String CUSTOMER_ENDPOINT =
        "/api/v1/customers";

    @Container
    @ServiceConnection
    static final PostgreSQLContainer POSTGRESQL =
        new PostgreSQLContainer(
            "postgres:18.4-alpine"
        )
            .withDatabaseName(
                "payments_customer_conditional_test"
            )
            .withUsername("payments_test")
            .withPassword("payments_test_only");

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @MockitoBean
    private CurrentIdentityUser currentIdentityUser;

    @BeforeEach
    void clearStoredData() {
        when(currentIdentityUser.requireUserId())
            .thenReturn(UUID.randomUUID());
        jdbcTemplate.update(
            "DELETE FROM customer_profile"
        );
    }

    @Test
    void customerReadReturnsStrongVersionEtag()
        throws Exception {
        UUID customerId =
            insertCustomer();

        mockMvc.perform(
                get(customerEndpoint(customerId))
            )
            .andExpect(status().isOk())
            .andExpect(
                header().string(
                    HttpHeaders.ETAG,
                    "\"0\""
                )
            )
            .andExpect(
                header().string(
                    HttpHeaders.CACHE_CONTROL,
                    containsString("no-store")
                )
            );
    }

    @Test
    void renameRequiresIfMatch()
        throws Exception {
        UUID customerId =
            insertCustomer();

        rename(
            customerId,
            "Updated Customer",
            null
        )
            .andExpect(
                status().isPreconditionRequired()
            )
            .andExpect(
                content().contentTypeCompatibleWith(
                    MediaType.APPLICATION_PROBLEM_JSON
                )
            )
            .andExpect(
                jsonPath("$.code")
                    .value(
                        "CUSTOMER_VERSION_REQUIRED"
                    )
            );

        assertCustomerState(
            customerId,
            "Original Customer",
            "ACTIVE",
            0L
        );
    }

    @Test
    void malformedIfMatchIsRejected()
        throws Exception {
        UUID customerId =
            insertCustomer();

        rename(
            customerId,
            "Updated Customer",
            "0"
        )
            .andExpect(status().isBadRequest())
            .andExpect(
                jsonPath("$.code")
                    .value(
                        "CUSTOMER_VERSION_INVALID"
                    )
            );

        assertCustomerState(
            customerId,
            "Original Customer",
            "ACTIVE",
            0L
        );
    }

    @Test
    void matchingVersionRenamesAndReturnsNewEtag()
        throws Exception {
        UUID customerId =
            insertCustomer();

        rename(
            customerId,
            "Updated Customer",
            "\"0\""
        )
            .andExpect(status().isOk())
            .andExpect(
                header().string(
                    HttpHeaders.ETAG,
                    "\"1\""
                )
            )
            .andExpect(
                jsonPath("$.fullName")
                    .value("Updated Customer")
            )
            .andExpect(
                jsonPath("$.version")
                    .value(1)
            );

        assertCustomerState(
            customerId,
            "Updated Customer",
            "ACTIVE",
            1L
        );
    }

    @Test
    void matchingVersionUpdatesStatus()
        throws Exception {
        UUID customerId =
            insertCustomer();

        updateStatus(
            customerId,
            "SUSPENDED",
            "\"0\""
        )
            .andExpect(status().isOk())
            .andExpect(
                header().string(
                    HttpHeaders.ETAG,
                    "\"1\""
                )
            )
            .andExpect(
                jsonPath("$.status")
                    .value("SUSPENDED")
            );

        assertCustomerState(
            customerId,
            "Original Customer",
            "SUSPENDED",
            1L
        );
    }

    @Test
    void staleVersionCannotOverwriteNewerState()
        throws Exception {
        UUID customerId =
            insertCustomer();

        rename(
            customerId,
            "First Update",
            "\"0\""
        )
            .andExpect(status().isOk());

        updateStatus(
            customerId,
            "SUSPENDED",
            "\"0\""
        )
            .andExpect(
                status().isPreconditionFailed()
            )
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
                        "CUSTOMER_VERSION_CONFLICT"
                    )
            )
            .andExpect(
                jsonPath("$.expectedVersion")
                    .value(0)
            )
            .andExpect(
                jsonPath("$.actualVersion")
                    .value(1)
            );

        assertCustomerState(
            customerId,
            "First Update",
            "ACTIVE",
            1L
        );
    }

    private org.springframework.test.web.servlet.ResultActions
    rename(
        UUID customerId,
        String fullName,
        String ifMatch
    ) throws Exception {
        var request =
            put(
                customerEndpoint(customerId)
                    + "/name"
            )
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
                );

        if (ifMatch != null) {
            request.header(
                HttpHeaders.IF_MATCH,
                ifMatch
            );
        }

        return mockMvc.perform(request);
    }

    private org.springframework.test.web.servlet.ResultActions
    updateStatus(
        UUID customerId,
        String statusValue,
        String ifMatch
    ) throws Exception {
        var request =
            put(
                customerEndpoint(customerId)
                    + "/status"
            )
                .with(csrf())
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
                );

        if (ifMatch != null) {
            request.header(
                HttpHeaders.IF_MATCH,
                ifMatch
            );
        }

        return mockMvc.perform(request);
    }

    private static String customerEndpoint(
        UUID customerId
    ) {
        return CUSTOMER_ENDPOINT
            + "/"
            + customerId;
    }

    private UUID insertCustomer() {
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
            "Original Customer",
            "ACTIVE",
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

    private void assertCustomerState(
        UUID customerId,
        String expectedName,
        String expectedStatus,
        long expectedVersion
    ) {
        CustomerState state =
            jdbcTemplate.queryForObject(
                """
                SELECT full_name, status, version
                FROM customer_profile
                WHERE id = ?
                """,
                (resultSet, rowNumber) ->
                    new CustomerState(
                        resultSet.getString(
                            "full_name"
                        ),
                        resultSet.getString(
                            "status"
                        ),
                        resultSet.getLong(
                            "version"
                        )
                    ),
                customerId
            );

        assertThat(state)
            .isNotNull();

        assertThat(state.fullName())
            .isEqualTo(expectedName);

        assertThat(state.status())
            .isEqualTo(expectedStatus);

        assertThat(state.version())
            .isEqualTo(expectedVersion);
    }

    private record CustomerState(
        String fullName,
        String status,
        long version
    ) {
    }
}