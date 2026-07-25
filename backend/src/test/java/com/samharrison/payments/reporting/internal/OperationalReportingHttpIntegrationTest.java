package com.samharrison.payments.reporting.internal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
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
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
@DirtiesContext(
    classMode = DirtiesContext.ClassMode.AFTER_CLASS
)
class OperationalReportingHttpIntegrationTest {

    private static final Instant FROM =
        Instant.parse("2026-07-25T00:00:00Z");

    private static final Instant TO =
        Instant.parse("2026-07-26T00:00:00Z");

    private static final String WINDOW =
        "?from=2026-07-25T00:00:00Z"
            + "&to=2026-07-26T00:00:00Z";

    private static final UUID ACTOR_ID =
        UUID.fromString(
            "10000000-0000-0000-0000-000000000001"
        );

    private static final UUID CUSTOMER_ID =
        UUID.fromString(
            "20000000-0000-0000-0000-000000000001"
        );

    private static final UUID SOURCE_ACCOUNT_ID =
        UUID.fromString(
            "30000000-0000-0000-0000-000000000001"
        );

    private static final UUID DESTINATION_ACCOUNT_ID =
        UUID.fromString(
            "30000000-0000-0000-0000-000000000002"
        );

    private static final UUID COMPLETED_PAYMENT_ID =
        UUID.fromString(
            "40000000-0000-0000-0000-000000000001"
        );

    private static final UUID SETTLEMENT_IMPORT_ID =
        UUID.fromString(
            "50000000-0000-0000-0000-000000000001"
        );

    @Container
    @ServiceConnection
    static final PostgreSQLContainer POSTGRESQL =
        new PostgreSQLContainer(
            "postgres:18.4-alpine"
        )
            .withDatabaseName(
                "operational_reporting_test"
            )
            .withUsername("payments_test")
            .withPassword("payments_test_only");

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private PlatformTransactionManager
        transactionManager;

    @BeforeEach
    void seedOperationalEvidence() {
        jdbcTemplate.execute(
            """
            TRUNCATE TABLE
                business_audit_event,
                settlement_resolution,
                settlement_discrepancy,
                settlement_match_claim,
                settlement_result,
                settlement_record,
                settlement_import,
                payment,
                ledger_entry,
                ledger_transaction,
                customer_account,
                customer_profile,
                identity_user
            CASCADE
            """
        );

        TransactionTemplate transaction =
            new TransactionTemplate(
                transactionManager
            );
        transaction.executeWithoutResult(
            ignored -> seedFixture()
        );
    }

    @Test
    void operationsReadsExactPaymentSummaryAndCsv()
        throws Exception {
        mockMvc.perform(
                get(
                    "/api/v1/reports/"
                        + "operational-summary"
                        + WINDOW
                )
                    .with(
                        user("operations")
                            .roles("OPERATIONS")
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
                jsonPath("$.payment.submittedCount")
                    .value(3)
            )
            .andExpect(
                jsonPath("$.payment.terminalCount")
                    .value(3)
            )
            .andExpect(
                jsonPath("$.payment.completedCount")
                    .value(1)
            )
            .andExpect(
                jsonPath(
                    "$.payment."
                        + "completedAmountMinorUnits"
                ).value(1250)
            )
            .andExpect(
                jsonPath(
                    "$.payment.rejectionCodeCounts."
                        + "PAYMENT_INSUFFICIENT_FUNDS"
                ).value(1)
            )
            .andExpect(
                jsonPath(
                    "$.payment.failureCodeCounts."
                        + "PAYMENT_PROCESSING_FAILED"
                ).value(1)
            )
            .andExpect(
                jsonPath("$.settlement")
                    .value(nullValue())
            )
            .andExpect(
                jsonPath("$.reconciliation")
                    .value(nullValue())
            );

        MvcResult export =
            mockMvc.perform(
                    get(
                        "/api/v1/reports/"
                            + "payments.csv"
                            + WINDOW
                    )
                        .with(
                            user("operations")
                                .roles("OPERATIONS")
                        )
                )
                .andExpect(status().isOk())
                .andExpect(
                    content().contentTypeCompatibleWith(
                        "text/csv"
                    )
                )
                .andExpect(
                    header().string(
                        HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; "
                            + "filename=\"payments.csv\""
                    )
                )
                .andExpect(
                    header().string(
                        "X-Content-Type-Options",
                        "nosniff"
                    )
                )
                .andReturn();

        String csv =
            export
                .getResponse()
                .getContentAsString();

        assertThat(csv)
            .startsWith(
                "\"payment_id\","
                    + "\"actor_identity_user_id\""
            )
            .contains(
                "\"PAYMENT_INSUFFICIENT_FUNDS\""
            )
            .contains(
                "\"PAYMENT_PROCESSING_FAILED\""
            )
            .endsWith("\r\n");
        assertThat(csv.lines()).hasSize(4);

        mockMvc.perform(
                get(
                    "/api/v1/reports/"
                        + "settlements.csv"
                        + WINDOW
                )
                    .with(
                        user("operations")
                            .roles("OPERATIONS")
                    )
            )
            .andExpect(status().isForbidden());
    }

    @Test
    void analystReadsSettlementAndReconciliation()
        throws Exception {
        mockMvc.perform(
                get(
                    "/api/v1/reports/"
                        + "operational-summary"
                        + WINDOW
                )
                    .with(
                        user("analyst")
                            .roles(
                                "RECONCILIATION_ANALYST"
                            )
                    )
            )
            .andExpect(status().isOk())
            .andExpect(
                jsonPath("$.payment")
                    .value(nullValue())
            )
            .andExpect(
                jsonPath(
                    "$.settlement."
                        + "acceptedImportCount"
                ).value(1)
            )
            .andExpect(
                jsonPath(
                    "$.settlement.acceptedRowCount"
                ).value(2)
            )
            .andExpect(
                jsonPath("$.settlement.matchedCount")
                    .value(1)
            )
            .andExpect(
                jsonPath(
                    "$.settlement.discrepancyCount"
                ).value(1)
            )
            .andExpect(
                jsonPath(
                    "$.settlement.importOutcomeCounts."
                        + "WITH_DISCREPANCIES"
                ).value(1)
            )
            .andExpect(
                jsonPath(
                    "$.reconciliation."
                        + "discrepancyCodeCounts."
                        + "PAYMENT_NOT_FOUND"
                ).value(1)
            )
            .andExpect(
                jsonPath(
                    "$.reconciliation."
                        + "lifecycleStateCounts.RESOLVED"
                ).value(1)
            )
            .andExpect(
                jsonPath(
                    "$.reconciliation."
                        + "resolutionDecisionCounts."
                        + "ACCEPTED"
                ).value(1)
            );

        MvcResult settlementExport =
            mockMvc.perform(
                    get(
                        "/api/v1/reports/"
                            + "settlements.csv"
                            + WINDOW
                    )
                        .with(
                            user("analyst")
                                .roles(
                                    "RECONCILIATION_ANALYST"
                                )
                        )
                )
                .andExpect(status().isOk())
                .andReturn();

        assertThat(
            settlementExport
                .getResponse()
                .getContentAsString()
                .lines()
        ).hasSize(3);

        MvcResult reconciliationExport =
            mockMvc.perform(
                    get(
                        "/api/v1/reports/"
                            + "reconciliation.csv"
                            + WINDOW
                    )
                        .with(
                            user("analyst")
                                .roles(
                                    "RECONCILIATION_ANALYST"
                                )
                        )
                )
                .andExpect(status().isOk())
                .andReturn();

        String reconciliationCsv =
            reconciliationExport
                .getResponse()
                .getContentAsString();

        assertThat(reconciliationCsv)
            .contains("\"ACCEPTED\"")
            .doesNotContain("HYPERLINK")
            .doesNotContain("=dangerous");

        mockMvc.perform(
                get(
                    "/api/v1/reports/"
                        + "payments.csv"
                        + WINDOW
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

    @Test
    void auditExportAppliesRoleScopeBeforeRows()
        throws Exception {
        MvcResult operationsExport =
            mockMvc.perform(
                    get(
                        "/api/v1/reports/"
                            + "audit-events.csv"
                            + WINDOW
                    )
                        .with(
                            user("operations")
                                .roles("OPERATIONS")
                        )
                )
                .andExpect(status().isOk())
                .andReturn();

        String operationsCsv =
            operationsExport
                .getResponse()
                .getContentAsString();

        assertThat(operationsCsv)
            .contains("\"customer.created\"")
            .doesNotContain(
                "settlement.import-accepted"
            );
        assertThat(operationsCsv.lines())
            .hasSize(2);

        MvcResult administratorExport =
            mockMvc.perform(
                    get(
                        "/api/v1/reports/"
                            + "audit-events.csv"
                            + WINDOW
                    )
                        .with(
                            user("administrator")
                                .roles("ADMIN")
                        )
                )
                .andExpect(status().isOk())
                .andReturn();

        String administratorCsv =
            administratorExport
                .getResponse()
                .getContentAsString();

        assertThat(administratorCsv)
            .contains("\"customer.created\"")
            .contains(
                "\"settlement.import-accepted\""
            )
            .contains(
                "\"reconciliation.discrepancy-resolved\""
            );
        assertThat(administratorCsv.lines())
            .hasSize(4);
    }

    @Test
    void rejectsCustomersAndInvalidWindows()
        throws Exception {
        mockMvc.perform(
                get(
                    "/api/v1/reports/"
                        + "operational-summary"
                        + WINDOW
                )
                    .with(
                        user("customer")
                            .roles("CUSTOMER")
                    )
            )
            .andExpect(status().isForbidden());

        mockMvc.perform(
                get(
                    "/api/v1/reports/"
                        + "payments.csv"
                        + "?from=2026-01-01T00:00:00Z"
                        + "&to=2026-02-02T00:00:00Z"
                )
                    .with(
                        user("operations")
                            .roles("OPERATIONS")
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
                    .value("REPORT_QUERY_INVALID")
            );

        mockMvc.perform(
                get(
                    "/api/v1/reports/"
                        + "payments.csv"
                        + "?from=2026-07-25T00:00:00Z"
                )
                    .with(
                        user("operations")
                            .roles("OPERATIONS")
                    )
            )
            .andExpect(status().isBadRequest());
    }

    @Test
    void rejectsAuditExportAboveTenThousandRows()
        throws Exception {
        jdbcTemplate.update(
            """
            INSERT INTO business_audit_event (
                id,
                event_type,
                schema_version,
                occurred_at,
                recorded_at,
                actor_kind,
                actor_identity_user_id,
                subject_type,
                subject_identifier,
                source_module,
                source_record_type,
                source_record_identifier,
                source_event_identifier,
                correlation_identifier,
                metadata
            )
            SELECT
                MD5(
                    'overflow-event-' || value
                )::UUID,
                'customer.created',
                1,
                ?,
                ?,
                'SYSTEM',
                NULL,
                'customer',
                'overflow-' || value,
                'customer',
                'customer',
                'overflow-' || value,
                'overflow-' || value,
                'report-overflow',
                '{"status":"ACTIVE"}'
            FROM GENERATE_SERIES(1, 10001)
                AS generated(value)
            """,
            at("2026-07-25T16:00:00Z"),
            at("2026-07-25T16:00:00Z")
        );

        mockMvc.perform(
                get(
                    "/api/v1/reports/"
                        + "audit-events.csv"
                        + WINDOW
                )
                    .with(
                        user("operations")
                            .roles("OPERATIONS")
                    )
            )
            .andExpect(
                status().isUnprocessableContent()
            )
            .andExpect(
                jsonPath("$.code")
                    .value(
                        "REPORT_EXPORT_LIMIT_EXCEEDED"
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
    void appliesOperationalReportingIndexes() {
        Integer indexCount =
            jdbcTemplate.queryForObject(
                """
                SELECT COUNT(*)
                FROM pg_indexes
                WHERE schemaname = 'public'
                  AND indexname IN (
                    'idx_payment_created_report',
                    'idx_settlement_import_completed_report',
                    'idx_settlement_discrepancy_created_report',
                    'idx_settlement_resolution_decision_report'
                  )
                """,
                Integer.class
            );

        assertThat(indexCount).isEqualTo(4);
    }

    private void seedFixture() {
        seedIdentityAndAccounts();
        seedPayments();
        seedSettlementAndResolution();
        seedBusinessAudit();
    }

    private void seedIdentityAndAccounts() {
        jdbcTemplate.update(
            """
            INSERT INTO identity_user (
                id,
                email,
                normalized_email,
                password_hash,
                status,
                failed_login_attempts,
                locked_until,
                created_at,
                updated_at,
                version
            )
            VALUES (?, ?, ?, ?, 'ACTIVE', 0, NULL, ?, ?, 0)
            """,
            ACTOR_ID,
            "reporting@example.test",
            "reporting@example.test",
            "{bcrypt}not-a-real-secret",
            at("2026-07-24T08:00:00Z"),
            at("2026-07-24T08:00:00Z")
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
            VALUES (?, 'Reporting Fixture', 'ACTIVE', ?, ?, 0)
            """,
            CUSTOMER_ID,
            at("2026-07-24T08:00:00Z"),
            at("2026-07-24T08:00:00Z")
        );

        insertAccount(SOURCE_ACCOUNT_ID);
        insertAccount(DESTINATION_ACCOUNT_ID);
    }

    private void insertAccount(UUID accountId) {
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
            VALUES (?, ?, 'GBP', 0, 'ACTIVE', ?, ?, 0)
            """,
            accountId,
            CUSTOMER_ID,
            at("2026-07-24T08:00:00Z"),
            at("2026-07-24T08:00:00Z")
        );
    }

    private void seedPayments() {
        UUID ledgerTransactionId =
            UUID.fromString(
                "60000000-0000-0000-0000-000000000001"
            );

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
            VALUES (
                ?,
                'INTERNAL_PAYMENT',
                ?,
                NULL,
                ?,
                'Reporting fixture payment'
            )
            """,
            ledgerTransactionId,
            COMPLETED_PAYMENT_ID.toString(),
            at("2026-07-25T10:00:00Z")
        );

        insertLedgerEntry(
            ledgerTransactionId,
            SOURCE_ACCOUNT_ID,
            "DEBIT",
            1
        );
        insertLedgerEntry(
            ledgerTransactionId,
            DESTINATION_ACCOUNT_ID,
            "CREDIT",
            2
        );

        insertPayment(
            COMPLETED_PAYMENT_ID,
            "COMPLETED",
            ledgerTransactionId,
            null,
            null,
            1250L,
            "2026-07-25T09:00:00Z",
            "2026-07-25T10:00:00Z"
        );
        insertPayment(
            UUID.fromString(
                "40000000-0000-0000-0000-000000000002"
            ),
            "REJECTED",
            null,
            "INSUFFICIENT_FUNDS",
            null,
            5000L,
            "2026-07-25T11:00:00Z",
            "2026-07-25T11:01:00Z"
        );
        insertPayment(
            UUID.fromString(
                "40000000-0000-0000-0000-000000000003"
            ),
            "FAILED",
            null,
            null,
            "PROCESSING_FAILED",
            750L,
            "2026-07-25T12:00:00Z",
            "2026-07-25T12:01:00Z"
        );
    }

    private void insertLedgerEntry(
        UUID transactionId,
        UUID accountId,
        String side,
        int sequence
    ) {
        jdbcTemplate.update(
            """
            INSERT INTO ledger_entry (
                id,
                transaction_id,
                ledger_account_id,
                side,
                amount_minor_units,
                currency,
                entry_sequence,
                description
            )
            VALUES (?, ?, ?, ?, 1250, 'GBP', ?, ?)
            """,
            UUID.randomUUID(),
            transactionId,
            accountId,
            side,
            sequence,
            "Reporting fixture " + side
        );
    }

    private void insertPayment(
        UUID paymentId,
        String status,
        UUID ledgerTransactionId,
        String rejectionReason,
        String failureReason,
        long amount,
        String createdAt,
        String updatedAt
    ) {
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
            VALUES (
                ?, ?, ?, ?, ?, 'GBP', ?, ?, ?, ?, ?, ?, 0
            )
            """,
            paymentId,
            ACTOR_ID,
            SOURCE_ACCOUNT_ID,
            DESTINATION_ACCOUNT_ID,
            amount,
            status,
            ledgerTransactionId,
            rejectionReason,
            failureReason,
            at(createdAt),
            at(updatedAt)
        );
    }

    private void seedSettlementAndResolution() {
        UUID matchedRecordId =
            UUID.fromString(
                "70000000-0000-0000-0000-000000000001"
            );
        UUID discrepancyRecordId =
            UUID.fromString(
                "70000000-0000-0000-0000-000000000002"
            );
        UUID discrepancyResultId =
            UUID.fromString(
                "80000000-0000-0000-0000-000000000002"
            );
        UUID discrepancyId =
            UUID.fromString(
                "90000000-0000-0000-0000-000000000001"
            );

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
                ?,
                ?,
                128,
                'fixture.csv',
                ?,
                'PROCESSING',
                NULL,
                NULL,
                NULL,
                ?,
                NULL,
                0
            )
            """,
            SETTLEMENT_IMPORT_ID,
            "a".repeat(64),
            ACTOR_ID,
            at("2026-07-25T13:00:00Z")
        );

        insertSettlementRecord(
            matchedRecordId,
            1,
            "SETTLEMENT-001",
            COMPLETED_PAYMENT_ID,
            1250L
        );
        insertSettlementRecord(
            discrepancyRecordId,
            2,
            "SETTLEMENT-002",
            UUID.fromString(
                "40000000-0000-0000-0000-000000000099"
            ),
            900L
        );

        insertSettlementResult(
            UUID.fromString(
                "80000000-0000-0000-0000-000000000001"
            ),
            matchedRecordId,
            1,
            "MATCHED",
            null
        );
        jdbcTemplate.update(
            """
            INSERT INTO settlement_match_claim (
                payment_id,
                settlement_record_id,
                claimed_at
            )
            VALUES (?, ?, ?)
            """,
            COMPLETED_PAYMENT_ID,
            matchedRecordId,
            at("2026-07-25T13:04:00Z")
        );
        insertSettlementResult(
            discrepancyResultId,
            discrepancyRecordId,
            2,
            "DISCREPANCY",
            "PAYMENT_NOT_FOUND"
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
            SETTLEMENT_IMPORT_ID,
            discrepancyResultId,
            discrepancyRecordId,
            at("2026-07-25T13:05:00Z")
        );

        jdbcTemplate.update(
            """
            UPDATE settlement_import
            SET
                status = 'COMPLETED',
                row_count = 2,
                matched_count = 1,
                discrepancy_count = 1,
                completed_at = ?,
                version = 1
            WHERE id = ?
            """,
            at("2026-07-25T13:10:00Z"),
            SETTLEMENT_IMPORT_ID
        );

        jdbcTemplate.update(
            """
            INSERT INTO settlement_resolution (
                id,
                settlement_discrepancy_id,
                actor_identity_user_id,
                decision,
                reason,
                discrepancy_version,
                decided_at
            )
            VALUES (?, ?, ?, 'ACCEPTED', ?, 0, ?)
            """,
            UUID.fromString(
                "91000000-0000-0000-0000-000000000001"
            ),
            discrepancyId,
            ACTOR_ID,
            "=dangerous HYPERLINK formula",
            at("2026-07-25T14:00:00Z")
        );

        jdbcTemplate.update(
            """
            UPDATE settlement_discrepancy
            SET status = 'RESOLVED', version = 1
            WHERE id = ?
            """,
            discrepancyId
        );
    }

    private void insertSettlementRecord(
        UUID recordId,
        int rowNumber,
        String externalId,
        UUID paymentId,
        long amount
    ) {
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
            VALUES (?, ?, ?, ?, ?, ?, 'GBP', ?)
            """,
            recordId,
            SETTLEMENT_IMPORT_ID,
            rowNumber,
            externalId,
            paymentId,
            amount,
            at("2026-07-25T12:30:00Z")
        );
    }

    private void insertSettlementResult(
        UUID resultId,
        UUID recordId,
        int rowNumber,
        String outcome,
        String discrepancyCode
    ) {
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
            VALUES (?, ?, ?, ?, ?, ?, ?)
            """,
            resultId,
            SETTLEMENT_IMPORT_ID,
            recordId,
            rowNumber,
            outcome,
            discrepancyCode,
            at("2026-07-25T13:04:00Z")
        );
    }

    private void seedBusinessAudit() {
        insertBusinessAudit(
            UUID.fromString(
                "a0000000-0000-0000-0000-000000000001"
            ),
            "customer.created",
            "customer",
            CUSTOMER_ID.toString(),
            "customer",
            "customer",
            CUSTOMER_ID.toString(),
            "customer-created",
            "{\"status\":\"ACTIVE\"}",
            "2026-07-25T08:00:00Z"
        );
        insertBusinessAudit(
            UUID.fromString(
                "a0000000-0000-0000-0000-000000000002"
            ),
            "settlement.import-accepted",
            "settlement_import",
            SETTLEMENT_IMPORT_ID.toString(),
            "reconciliation",
            "settlement_import",
            SETTLEMENT_IMPORT_ID.toString(),
            "import-completed",
            "{\"rowCount\":2,"
                + "\"matchedCount\":1,"
                + "\"discrepancyCount\":1}",
            "2026-07-25T13:10:00Z"
        );
    }

    private void insertBusinessAudit(
        UUID eventId,
        String eventType,
        String subjectType,
        String subjectIdentifier,
        String sourceModule,
        String sourceRecordType,
        String sourceRecordIdentifier,
        String sourceEventIdentifier,
        String metadata,
        String occurredAt
    ) {
        jdbcTemplate.update(
            """
            INSERT INTO business_audit_event (
                id,
                event_type,
                schema_version,
                occurred_at,
                recorded_at,
                actor_kind,
                actor_identity_user_id,
                subject_type,
                subject_identifier,
                source_module,
                source_record_type,
                source_record_identifier,
                source_event_identifier,
                correlation_identifier,
                metadata
            )
            VALUES (
                ?, ?, 1, ?, ?, 'SYSTEM', NULL,
                ?, ?, ?, ?, ?, ?, 'report-fixture', ?
            )
            """,
            eventId,
            eventType,
            at(occurredAt),
            at(occurredAt),
            subjectType,
            subjectIdentifier,
            sourceModule,
            sourceRecordType,
            sourceRecordIdentifier,
            sourceEventIdentifier,
            metadata
        );
    }

    private static java.time.OffsetDateTime at(
        String instant
    ) {
        return Instant
            .parse(instant)
            .atOffset(ZoneOffset.UTC);
    }
}
