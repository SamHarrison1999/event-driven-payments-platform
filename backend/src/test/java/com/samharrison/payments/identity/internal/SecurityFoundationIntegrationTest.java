package com.samharrison.payments.identity.internal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
class SecurityFoundationIntegrationTest {

    private static final String CSRF_ENDPOINT =
        "/api/v1/identity/csrf";

    @Container
    @ServiceConnection
    static final PostgreSQLContainer POSTGRESQL =
        new PostgreSQLContainer(
            "postgres:18.4-alpine"
        )
            .withDatabaseName(
                "payments_security_test"
            )
            .withUsername("payments_test")
            .withPassword("payments_test_only");

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void clearSessions() {
        jdbcTemplate.update(
            "DELETE FROM spring_session_attributes"
        );

        jdbcTemplate.update(
            "DELETE FROM spring_session"
        );
    }

    @Test
    void appliesTheJdbcSessionMigration() {
        Long migrationCount =
            jdbcTemplate.queryForObject(
                """
                SELECT COUNT(*)
                FROM public.flyway_schema_history
                WHERE version = '3'
                  AND success = TRUE
                """,
                Long.class
            );

        assertThat(migrationCount)
            .isEqualTo(1L);
    }

    @Test
    void createsAPostgresqlBackedCsrfSession()
        throws Exception {
        mockMvc.perform(
                get(CSRF_ENDPOINT)
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
                jsonPath("$.headerName")
                    .value("X-CSRF-TOKEN")
            )
            .andExpect(
                jsonPath("$.parameterName")
                    .value("_csrf")
            )
            .andExpect(
                jsonPath("$.token")
                    .isNotEmpty()
            );

        Long sessionCount =
            jdbcTemplate.queryForObject(
                """
                SELECT COUNT(*)
                FROM spring_session
                """,
                Long.class
            );

        assertThat(sessionCount)
            .isEqualTo(1L);
    }

    @Test
    void rejectsRegistrationWithoutCsrf()
        throws Exception {
        mockMvc.perform(
                post(
                    "/api/v1/identity/registrations"
                )
                    .contentType(
                        MediaType.APPLICATION_JSON
                    )
                    .content(
                        """
                        {
                          "email":
                            "sam.customer@example.com",
                          "password":
                            "this is a secure passphrase"
                        }
                        """
                    )
            )
            .andExpect(status().isForbidden())
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
                    .value("SECURITY_ACCESS_DENIED")
            );
    }

    @Test
    void keepsSystemInformationPublic()
        throws Exception {
        mockMvc.perform(
                get("/api/v1/system/info")
            )
            .andExpect(status().isOk());
    }

    @Test
    void rejectsUnauthenticatedProtectedRequests()
        throws Exception {
        mockMvc.perform(
                get("/api/v1/accounts")
            )
            .andExpect(status().isUnauthorized())
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
                        "SECURITY_AUTHENTICATION_REQUIRED"
                    )
            );
    }
}
