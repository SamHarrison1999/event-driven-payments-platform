package com.samharrison.payments.account.internal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

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
class AccountConditionalUpdateHttpIntegrationTest {

    private static final String ACCOUNT_ENDPOINT =
        "/api/v1/accounts";

    @Container
    @ServiceConnection
    static final PostgreSQLContainer POSTGRESQL =
        new PostgreSQLContainer(
            "postgres:18.4-alpine"
        )
            .withDatabaseName(
                "payments_account_conditional_test"
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
    }

    @Test
    void accountReadReturnsStrongVersionEtag()
        throws Exception {
        UUID accountId =
            insertAccount();

        mockMvc.perform(
                get(accountEndpoint(accountId))
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
    void lifecycleUpdateRequiresIfMatch()
        throws Exception {
        UUID accountId =
            insertAccount();

        updateStatus(
            accountId,
            "FROZEN",
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
                        "ACCOUNT_VERSION_REQUIRED"
                    )
            );

        assertAccountState(
            accountId,
            "ACTIVE",
            0L
        );
    }

    @Test
    void malformedIfMatchIsRejected()
        throws Exception {
        UUID accountId =
            insertAccount();

        updateStatus(
            accountId,
            "FROZEN",
            "0"
        )
            .andExpect(status().isBadRequest())
            .andExpect(
                jsonPath("$.code")
                    .value(
                        "ACCOUNT_VERSION_INVALID"
                    )
            );

        assertAccountState(
            accountId,
            "ACTIVE",
            0L
        );
    }

    @Test
    void matchingVersionUpdatesAndReturnsNewEtag()
        throws Exception {
        UUID accountId =
            insertAccount();

        updateStatus(
            accountId,
            "FROZEN",
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
                    .value("FROZEN")
            )
            .andExpect(
                jsonPath("$.version")
                    .value(1)
            );

        assertAccountState(
            accountId,
            "FROZEN",
            1L
        );
    }

    @Test
    void staleVersionCannotOverwriteNewerState()
        throws Exception {
        UUID accountId =
            insertAccount();

        updateStatus(
            accountId,
            "FROZEN",
            "\"0\""
        )
            .andExpect(status().isOk());

        updateStatus(
            accountId,
            "ACTIVE",
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
                        "ACCOUNT_VERSION_CONFLICT"
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

        assertAccountState(
            accountId,
            "FROZEN",
            1L
        );
    }

    private org.springframework.test.web.servlet.ResultActions
    updateStatus(
        UUID accountId,
        String statusValue,
        String ifMatch
    ) throws Exception {
        var request =
            put(
                accountEndpoint(accountId)
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

    private static String accountEndpoint(
        UUID accountId
    ) {
        return ACCOUNT_ENDPOINT
            + "/"
            + accountId;
    }

    private UUID insertAccount() {
        UUID customerId =
            UUID.randomUUID();

        UUID accountId =
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
            "Conditional Account Customer",
            "ACTIVE",
            timestamp.atOffset(
                ZoneOffset.UTC
            ),
            timestamp.atOffset(
                ZoneOffset.UTC
            ),
            0L
        );

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
            0L,
            "ACTIVE",
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

    private void assertAccountState(
        UUID accountId,
        String expectedStatus,
        long expectedVersion
    ) {
        String statusValue =
            jdbcTemplate.queryForObject(
                """
                SELECT status
                FROM customer_account
                WHERE id = ?
                """,
                String.class,
                accountId
            );

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

        assertThat(statusValue)
            .isEqualTo(expectedStatus);

        assertThat(version)
            .isEqualTo(expectedVersion);
    }
}