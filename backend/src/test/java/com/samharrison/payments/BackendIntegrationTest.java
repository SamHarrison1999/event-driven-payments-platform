package com.samharrison.payments;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.samharrison.payments.shared.infrastructure.web.CorrelationIdFilter;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
class BackendIntegrationTest {

    private static final String TEST_CORRELATION_ID =
        "integration-test-correlation";

    @Container
    @ServiceConnection
    static final PostgreSQLContainer POSTGRESQL =
        new PostgreSQLContainer("postgres:18.4-alpine")
            .withDatabaseName("payments_platform_test")
            .withUsername("payments_test")
            .withPassword("payments_test_only");

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void appliesTheFlywayBaselineMigration() {
        Long migrationCount = jdbcTemplate.queryForObject(
            """
            SELECT COUNT(*)
            FROM public.flyway_schema_history
            WHERE version = '1'
              AND success = TRUE
            """,
            Long.class
        );

        assertThat(migrationCount)
            .isEqualTo(1L);
    }

    @Test
    void returnsEducationalSystemInformation() throws Exception {
        mockMvc.perform(
                get("/api/v1/system/info")
                    .header(
                        CorrelationIdFilter.HEADER_NAME,
                        TEST_CORRELATION_ID
                    )
            )
            .andExpect(status().isOk())
            .andExpect(
                content().contentTypeCompatibleWith(
                    MediaType.APPLICATION_JSON
                )
            )
            .andExpect(
                header().string(
                    CorrelationIdFilter.HEADER_NAME,
                    TEST_CORRELATION_ID
                )
            )
            .andExpect(
                jsonPath("$.name")
                    .value(
                        "Event-Driven Payments and "
                            + "Reconciliation Platform"
                    )
            )
            .andExpect(
                jsonPath("$.educational")
                    .value(true)
            )
            .andExpect(
                jsonPath("$.realMoneyProcessing")
                    .value(false)
            );
    }

    @Test
    void generatesACorrelationIdWhenNoneIsSupplied()
        throws Exception {
        mockMvc.perform(
                get("/api/v1/system/info")
            )
            .andExpect(status().isOk())
            .andExpect(
                header().exists(
                    CorrelationIdFilter.HEADER_NAME
                )
            );
    }

    @Test
    void exposesTheOpenApiDocument() throws Exception {
        mockMvc.perform(
                get("/v3/api-docs")
            )
            .andExpect(status().isOk())
            .andExpect(
                content().contentTypeCompatibleWith(
                    MediaType.APPLICATION_JSON
                )
            )
            .andExpect(
                jsonPath("$.info.title")
                    .value(
                        "Event-Driven Payments and "
                            + "Reconciliation Platform"
                    )
            )
            .andExpect(
                jsonPath("$.info.version")
                    .value("0.0.1-SNAPSHOT")
            );
    }

    @Test
    void exposesApplicationHealth() throws Exception {
        mockMvc.perform(
                get("/actuator/health")
            )
            .andExpect(status().isOk())
            .andExpect(
                jsonPath("$.status")
                    .value("UP")
            );
    }
}
