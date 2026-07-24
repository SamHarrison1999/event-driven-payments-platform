package com.samharrison.payments.reconciliation.internal;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.samharrison.payments.identity.CurrentIdentityUser;
import java.nio.charset.StandardCharsets;
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
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
@Transactional
class SettlementImportHttpIntegrationTest {

    private static final String IMPORT_ENDPOINT =
        "/api/v1/settlement-imports";

    private static final Instant CREATED_AT =
        Instant.parse("2026-07-24T10:00:00Z");

    @Container
    @ServiceConnection
    static final PostgreSQLContainer POSTGRESQL =
        new PostgreSQLContainer(
            "postgres:18.4-alpine"
        )
            .withDatabaseName(
                "settlement_http_test"
            )
            .withUsername("settlement_http_test")
            .withPassword(
                "settlement_http_test_only"
            );

    @Autowired
    private MockMvc mockMvc;

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
    void createsAndReplaysOneAtomicImport()
        throws Exception {
        UUID paymentId =
            insertCompletedPayment(250L);
        byte[] fileBytes =
            validCsv(
                "settlement-http-replay",
                paymentId,
                250L
            );

        MvcResult created =
            mockMvc.perform(
                    multipart(IMPORT_ENDPOINT)
                        .file(
                            settlementFile(fileBytes)
                        )
                        .with(csrf())
                        .with(
                            user("analyst")
                                .roles(
                                    "RECONCILIATION_ANALYST"
                                )
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
                            IMPORT_ENDPOINT + "/"
                        )
                    )
                )
                .andExpect(
                    jsonPath("$.existingImport")
                        .value(false)
                )
                .andExpect(
                    jsonPath("$.rowCount").value(1)
                )
                .andExpect(
                    jsonPath("$.matchedCount").value(1)
                )
                .andExpect(
                    jsonPath("$.discrepancyCount")
                        .value(0)
                )
                .andReturn();

        String location =
            created
                .getResponse()
                .getHeader(HttpHeaders.LOCATION);
        String importId =
            java.util.Objects
                .requireNonNull(location)
                .substring(
                    location.lastIndexOf('/') + 1
                );

        mockMvc.perform(
                multipart(IMPORT_ENDPOINT)
                    .file(settlementFile(fileBytes))
                    .with(csrf())
                    .with(
                        user("analyst")
                            .roles(
                                "RECONCILIATION_ANALYST"
                            )
                    )
            )
            .andExpect(status().isOk())
            .andExpect(
                jsonPath("$.importId")
                    .value(importId)
            )
            .andExpect(
                jsonPath("$.existingImport")
                    .value(true)
            );

        mockMvc.perform(
                get(
                    IMPORT_ENDPOINT
                        + "/"
                        + importId
                        + "/results"
                )
                    .param("limit", "1")
                    .with(
                        user("analyst")
                            .roles(
                                "RECONCILIATION_ANALYST"
                            )
                    )
            )
            .andExpect(status().isOk())
            .andExpect(
                header().string(
                    HttpHeaders.CACHE_CONTROL,
                    containsString("no-store")
                )
            )
            .andExpect(
                jsonPath("$.results[0].outcome")
                    .value("MATCHED")
            )
            .andExpect(
                jsonPath("$.nextAfterRowNumber")
                    .doesNotExist()
            );
    }

    @Test
    void rejectsInvalidRowsWithStableProblemDetails()
        throws Exception {
        UUID paymentId = UUID.randomUUID();
        byte[] invalidFile =
            validCsv(
                "settlement-http-invalid",
                paymentId,
                0L
            );

        mockMvc.perform(
                multipart(IMPORT_ENDPOINT)
                    .file(settlementFile(invalidFile))
                    .with(csrf())
                    .with(
                        user("analyst")
                            .roles(
                                "RECONCILIATION_ANALYST"
                            )
                    )
            )
            .andExpect(status().isBadRequest())
            .andExpect(
                header().string(
                    HttpHeaders.CACHE_CONTROL,
                    containsString("no-store")
                )
            )
            .andExpect(
                jsonPath("$.code")
                    .value("INVALID_AMOUNT")
            )
            .andExpect(
                jsonPath("$.rowNumber").value(1)
            );
    }

    @Test
    void customerRoleCannotImportSettlements()
        throws Exception {
        mockMvc.perform(
                multipart(IMPORT_ENDPOINT)
                    .file(
                        settlementFile(
                            validCsv(
                                "settlement-forbidden",
                                UUID.randomUUID(),
                                100L
                            )
                        )
                    )
                    .with(csrf())
                    .with(
                        user("customer")
                            .roles("CUSTOMER")
                    )
            )
            .andExpect(status().isForbidden());
    }

    @Test
    void importRequiresCsrfProtection()
        throws Exception {
        mockMvc.perform(
                multipart(IMPORT_ENDPOINT)
                    .file(
                        settlementFile(
                            validCsv(
                                "settlement-csrf",
                                UUID.randomUUID(),
                                100L
                            )
                        )
                    )
                    .with(
                        user("analyst")
                            .roles(
                                "RECONCILIATION_ANALYST"
                            )
                    )
            )
            .andExpect(status().isForbidden());
    }

    private UUID insertIdentityUser() {
        UUID userId = UUID.randomUUID();
        String email =
            userId + "@settlement-http.test";

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
            "settlement-http-test-password-hash",
            CREATED_AT.atOffset(ZoneOffset.UTC),
            CREATED_AT.atOffset(ZoneOffset.UTC)
        );

        return userId;
    }

    private UUID insertCompletedPayment(
        long amountMinorUnits
    ) {
        UUID ledgerTransactionId = UUID.randomUUID();
        UUID paymentId = UUID.randomUUID();

        jdbcTemplate.update(
            """
            INSERT INTO ledger_transaction (
                id,
                transaction_type,
                business_reference,
                corrects_transaction_id,
                posted_at,
                description
            )
            VALUES (?, 'PAYMENT', ?, NULL, ?, ?)
            """,
            ledgerTransactionId,
            "settlement-http-"
                + ledgerTransactionId,
            CREATED_AT.atOffset(ZoneOffset.UTC),
            "Settlement HTTP test"
        );

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
            VALUES (?, ?, ?, ?, ?, 'GBP', 'COMPLETED',
                ?, NULL, NULL, ?, ?, 0)
            """,
            paymentId,
            actorId,
            UUID.randomUUID(),
            UUID.randomUUID(),
            amountMinorUnits,
            ledgerTransactionId,
            CREATED_AT.atOffset(ZoneOffset.UTC),
            CREATED_AT.atOffset(ZoneOffset.UTC)
        );

        return paymentId;
    }

    private static MockMultipartFile settlementFile(
        byte[] fileBytes
    ) {
        return new MockMultipartFile(
            "file",
            "daily.csv",
            "text/csv",
            fileBytes
        );
    }

    private static byte[] validCsv(
        String settlementRecordId,
        UUID paymentId,
        long amountMinorUnits
    ) {
        String content =
            "settlement_record_id,payment_id,"
                + "amount_minor_units,currency,"
                + "settled_at\r\n"
                + settlementRecordId
                + ","
                + paymentId
                + ","
                + amountMinorUnits
                + ",GBP,"
                + CREATED_AT
                + "\r\n";

        return content.getBytes(
            StandardCharsets.UTF_8
        );
    }
}
