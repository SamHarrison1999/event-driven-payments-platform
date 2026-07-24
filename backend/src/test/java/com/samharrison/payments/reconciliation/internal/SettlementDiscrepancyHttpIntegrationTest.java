package com.samharrison.payments.reconciliation.internal;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
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
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
@Transactional
class SettlementDiscrepancyHttpIntegrationTest {

    private static final String ENDPOINT =
        "/api/v1/settlement-discrepancies";

    private static final Instant CREATED_AT =
        Instant.parse("2026-07-24T10:00:00Z");

    @Container
    @ServiceConnection
    static final PostgreSQLContainer POSTGRESQL =
        new PostgreSQLContainer(
            "postgres:18.4-alpine"
        )
            .withDatabaseName(
                "settlement_discrepancy_http_test"
            )
            .withUsername(
                "settlement_discrepancy_http_test"
            )
            .withPassword(
                "settlement_discrepancy_http_only"
            );

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @MockitoBean
    private CurrentIdentityUser currentIdentityUser;

    private UUID actorId;

    @BeforeEach
    void createActor() {
        actorId = insertIdentityUser();

        when(currentIdentityUser.requireUserId())
            .thenReturn(actorId);
    }

    @Test
    void listsOpenDiscrepanciesWithKeysetPagination()
        throws Exception {
        UUID firstId =
            createOpenDiscrepancy(CREATED_AT);
        UUID secondId =
            createOpenDiscrepancy(
                CREATED_AT.plusSeconds(1L)
            );

        MvcResult firstPage =
            mockMvc.perform(
                    get(ENDPOINT)
                        .param("limit", "1")
                        .with(analyst())
                )
                .andExpect(status().isOk())
                .andExpect(
                    header().string(
                        HttpHeaders.CACHE_CONTROL,
                        containsString("no-store")
                    )
                )
                .andExpect(
                    jsonPath(
                        "$.discrepancies[0]"
                            + ".discrepancyId"
                    )
                        .value(firstId.toString())
                )
                .andExpect(
                    jsonPath("$.nextAfterCreatedAt")
                        .value(CREATED_AT.toString())
                )
                .andExpect(
                    jsonPath("$.nextAfterId")
                        .value(firstId.toString())
                )
                .andReturn();

        JsonNode firstBody =
            objectMapper.readTree(
                firstPage
                    .getResponse()
                    .getContentAsByteArray()
            );

        mockMvc.perform(
                get(ENDPOINT)
                    .param(
                        "afterCreatedAt",
                        firstBody
                            .get("nextAfterCreatedAt")
                            .asString()
                    )
                    .param(
                        "afterId",
                        firstBody
                            .get("nextAfterId")
                            .asString()
                    )
                    .param("limit", "1")
                    .with(analyst())
            )
            .andExpect(status().isOk())
            .andExpect(
                jsonPath(
                    "$.discrepancies[0].discrepancyId"
                )
                    .value(secondId.toString())
            )
            .andExpect(
                jsonPath("$.nextAfterCreatedAt")
                    .doesNotExist()
            )
            .andExpect(
                jsonPath("$.nextAfterId")
                    .doesNotExist()
            );
    }

    @Test
    void readsOpenDiscrepancyWithStrongEtag()
        throws Exception {
        UUID discrepancyId =
            createOpenDiscrepancy(CREATED_AT);

        mockMvc.perform(
                get(ENDPOINT + "/" + discrepancyId)
                    .with(analyst())
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
            )
            .andExpect(
                jsonPath("$.status").value("OPEN")
            )
            .andExpect(
                jsonPath("$.code")
                    .value("PAYMENT_NOT_FOUND")
            )
            .andExpect(
                jsonPath("$.resolution")
                    .doesNotExist()
            );
    }

    @Test
    void resolvesWithAttributedEvidenceAndNewEtag()
        throws Exception {
        UUID discrepancyId =
            createOpenDiscrepancy(CREATED_AT);

        mockMvc.perform(
                put(
                    ENDPOINT
                        + "/"
                        + discrepancyId
                        + "/resolution"
                )
                    .header(HttpHeaders.IF_MATCH, "\"0\"")
                    .contentType(
                        MediaType.APPLICATION_JSON
                    )
                    .content(
                        """
                        {
                          "decision":
                            "EXTERNAL_CORRECTION_REQUIRED",
                          "reason":
                            "  Correct the external row.  "
                        }
                        """
                    )
                    .with(csrf())
                    .with(analyst())
            )
            .andExpect(status().isOk())
            .andExpect(
                header().string(
                    HttpHeaders.ETAG,
                    "\"1\""
                )
            )
            .andExpect(
                header().string(
                    HttpHeaders.CACHE_CONTROL,
                    containsString("no-store")
                )
            )
            .andExpect(
                jsonPath("$.status").value("RESOLVED")
            )
            .andExpect(
                jsonPath("$.version").value(1)
            )
            .andExpect(
                jsonPath(
                    "$.resolution.actorIdentityUserId"
                )
                    .value(actorId.toString())
            )
            .andExpect(
                jsonPath("$.resolution.decision")
                    .value(
                        "EXTERNAL_CORRECTION_REQUIRED"
                    )
            )
            .andExpect(
                jsonPath("$.resolution.reason")
                    .value("Correct the external row.")
            )
            .andExpect(
                jsonPath(
                    "$.resolution.discrepancyVersion"
                )
                    .value(0)
            );
    }

    @Test
    void requiresValidStrongIfMatch()
        throws Exception {
        UUID discrepancyId =
            createOpenDiscrepancy(CREATED_AT);
        String resolutionEndpoint =
            ENDPOINT
                + "/"
                + discrepancyId
                + "/resolution";

        mockMvc.perform(
                put(resolutionEndpoint)
                    .contentType(
                        MediaType.APPLICATION_JSON
                    )
                    .content(validResolution())
                    .with(csrf())
                    .with(analyst())
            )
            .andExpect(
                status().isPreconditionRequired()
            )
            .andExpect(
                jsonPath("$.code")
                    .value(
                        "SETTLEMENT_DISCREPANCY_"
                            + "VERSION_REQUIRED"
                    )
            )
            .andExpect(
                header().string(
                    HttpHeaders.CACHE_CONTROL,
                    containsString("no-store")
                )
            );

        mockMvc.perform(
                put(resolutionEndpoint)
                    .header(
                        HttpHeaders.IF_MATCH,
                        "W/\"0\""
                    )
                    .contentType(
                        MediaType.APPLICATION_JSON
                    )
                    .content(validResolution())
                    .with(csrf())
                    .with(analyst())
            )
            .andExpect(status().isBadRequest())
            .andExpect(
                jsonPath("$.code")
                    .value(
                        "SETTLEMENT_DISCREPANCY_"
                            + "VERSION_INVALID"
                    )
            );
    }

    @Test
    void staleResolutionReturnsPreconditionFailed()
        throws Exception {
        UUID discrepancyId =
            createOpenDiscrepancy(CREATED_AT);
        String endpoint =
            ENDPOINT
                + "/"
                + discrepancyId
                + "/resolution";

        mockMvc.perform(
                put(endpoint)
                    .header(HttpHeaders.IF_MATCH, "\"0\"")
                    .contentType(
                        MediaType.APPLICATION_JSON
                    )
                    .content(validResolution())
                    .with(csrf())
                    .with(analyst())
            )
            .andExpect(status().isOk());

        mockMvc.perform(
                put(endpoint)
                    .header(HttpHeaders.IF_MATCH, "\"0\"")
                    .contentType(
                        MediaType.APPLICATION_JSON
                    )
                    .content(validResolution())
                    .with(csrf())
                    .with(analyst())
            )
            .andExpect(
                status().isPreconditionFailed()
            )
            .andExpect(
                jsonPath("$.code")
                    .value(
                        "SETTLEMENT_DISCREPANCY_"
                            + "VERSION_CONFLICT"
                    )
            )
            .andExpect(
                jsonPath("$.expectedVersion").value(0)
            )
            .andExpect(
                jsonPath("$.actualVersion").value(1)
            );
    }

    @Test
    void rejectsInvalidReasonAndIncompleteCursor()
        throws Exception {
        UUID discrepancyId =
            createOpenDiscrepancy(CREATED_AT);

        mockMvc.perform(
                put(
                    ENDPOINT
                        + "/"
                        + discrepancyId
                        + "/resolution"
                )
                    .header(HttpHeaders.IF_MATCH, "\"0\"")
                    .contentType(
                        MediaType.APPLICATION_JSON
                    )
                    .content(
                        """
                        {
                          "decision": "ACCEPTED",
                          "reason": "line one\\nline two"
                        }
                        """
                    )
                    .with(csrf())
                    .with(analyst())
            )
            .andExpect(status().isBadRequest())
            .andExpect(
                jsonPath("$.code")
                    .value(
                        "SETTLEMENT_DISCREPANCY_"
                            + "REQUEST_INVALID"
                    )
            );

        mockMvc.perform(
                get(ENDPOINT)
                    .param(
                        "afterCreatedAt",
                        CREATED_AT.toString()
                    )
                    .with(analyst())
            )
            .andExpect(status().isBadRequest())
            .andExpect(
                jsonPath("$.code")
                    .value(
                        "SETTLEMENT_DISCREPANCY_"
                            + "REQUEST_INVALID"
                    )
            );
    }

    @Test
    void customerCannotReadOrResolveDiscrepancies()
        throws Exception {
        UUID discrepancyId =
            createOpenDiscrepancy(CREATED_AT);

        mockMvc.perform(
                get(ENDPOINT)
                    .with(
                        user("customer")
                            .roles("CUSTOMER")
                    )
            )
            .andExpect(status().isForbidden());

        mockMvc.perform(
                put(
                    ENDPOINT
                        + "/"
                        + discrepancyId
                        + "/resolution"
                )
                    .header(HttpHeaders.IF_MATCH, "\"0\"")
                    .contentType(
                        MediaType.APPLICATION_JSON
                    )
                    .content(validResolution())
                    .with(csrf())
                    .with(
                        user("customer")
                            .roles("CUSTOMER")
                    )
            )
            .andExpect(status().isForbidden());
    }

    @Test
    void resolutionRequiresCsrfProtection()
        throws Exception {
        UUID discrepancyId =
            createOpenDiscrepancy(CREATED_AT);

        mockMvc.perform(
                put(
                    ENDPOINT
                        + "/"
                        + discrepancyId
                        + "/resolution"
                )
                    .header(HttpHeaders.IF_MATCH, "\"0\"")
                    .contentType(
                        MediaType.APPLICATION_JSON
                    )
                    .content(validResolution())
                    .with(analyst())
            )
            .andExpect(status().isForbidden());
    }

    private RequestPostProcessor analyst() {
        return user("analyst")
            .roles("RECONCILIATION_ANALYST");
    }

    private static String validResolution() {
        return """
            {
              "decision": "ACCEPTED",
              "reason": "External evidence accepted."
            }
            """;
    }

    private UUID createOpenDiscrepancy(
        Instant createdAt
    ) {
        UUID importId = UUID.randomUUID();
        UUID recordId = UUID.randomUUID();
        UUID resultId = UUID.randomUUID();
        UUID discrepancyId = UUID.randomUUID();
        UUID paymentId = UUID.randomUUID();
        String fingerprint =
            UUID.randomUUID()
                .toString()
                .replace("-", "")
                .repeat(2);

        jdbcTemplate.update(
            """
            INSERT INTO settlement_import (
                id,
                raw_file_sha256,
                raw_file_size_bytes,
                original_filename,
                actor_identity_user_id,
                status,
                row_count,
                matched_count,
                discrepancy_count,
                created_at,
                completed_at,
                version
            )
            VALUES (
                ?, ?, 128, 'http-resolution-test.csv', ?,
                'PROCESSING', NULL, NULL, NULL,
                ?, NULL, 0
            )
            """,
            importId,
            fingerprint,
            actorId,
            createdAt.atOffset(ZoneOffset.UTC)
        );

        jdbcTemplate.update(
            """
            INSERT INTO settlement_record (
                id,
                settlement_import_id,
                row_number,
                settlement_record_id,
                payment_id,
                amount_minor_units,
                currency,
                settled_at
            )
            VALUES (?, ?, 1, ?, ?, 500, 'GBP', ?)
            """,
            recordId,
            importId,
            "http-resolution-" + recordId,
            paymentId,
            createdAt.atOffset(ZoneOffset.UTC)
        );

        jdbcTemplate.update(
            """
            INSERT INTO settlement_result (
                id,
                settlement_import_id,
                settlement_record_id,
                row_number,
                outcome,
                discrepancy_code,
                reconciled_at
            )
            VALUES (
                ?, ?, ?, 1, 'DISCREPANCY',
                'PAYMENT_NOT_FOUND', ?
            )
            """,
            resultId,
            importId,
            recordId,
            createdAt.atOffset(ZoneOffset.UTC)
        );

        jdbcTemplate.update(
            """
            INSERT INTO settlement_discrepancy (
                id,
                settlement_import_id,
                settlement_result_id,
                settlement_record_id,
                code,
                status,
                created_at,
                version
            )
            VALUES (
                ?, ?, ?, ?, 'PAYMENT_NOT_FOUND',
                'OPEN', ?, 0
            )
            """,
            discrepancyId,
            importId,
            resultId,
            recordId,
            createdAt.atOffset(ZoneOffset.UTC)
        );

        jdbcTemplate.update(
            """
            UPDATE settlement_import
            SET
                status = 'COMPLETED',
                row_count = 1,
                matched_count = 0,
                discrepancy_count = 1,
                completed_at = ?,
                version = 1
            WHERE id = ?
            """,
            createdAt
                .plusSeconds(1L)
                .atOffset(ZoneOffset.UTC),
            importId
        );

        return discrepancyId;
    }

    private UUID insertIdentityUser() {
        UUID userId = UUID.randomUUID();
        String email =
            userId + "@settlement-discrepancy-http.test";

        jdbcTemplate.update(
            """
            INSERT INTO identity_user (
                id,
                email,
                normalized_email,
                password_hash,
                status,
                failed_login_attempts,
                created_at,
                updated_at,
                version
            )
            VALUES (?, ?, ?, ?, 'ACTIVE', 0, ?, ?, 0)
            """,
            userId,
            email,
            email,
            "settlement-discrepancy-http-password-hash",
            CREATED_AT.atOffset(ZoneOffset.UTC),
            CREATED_AT.atOffset(ZoneOffset.UTC)
        );

        return userId;
    }
}
