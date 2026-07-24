package com.samharrison.payments.reconciliation.internal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

@SpringBootTest
@Testcontainers
@DirtiesContext(
    classMode = DirtiesContext.ClassMode.AFTER_CLASS
)
@WithMockUser(roles = "RECONCILIATION_ANALYST")
@Transactional
class SettlementImportWorkflowIntegrationTest {

    private static final Instant CREATED_AT =
        Instant.parse("2026-07-24T10:00:00Z");

    @Container
    @ServiceConnection
    static final PostgreSQLContainer POSTGRESQL =
        new PostgreSQLContainer(
            "postgres:18.4-alpine"
        )
            .withDatabaseName(
                "settlement_workflow_test"
            )
            .withUsername("settlement_test")
            .withPassword("settlement_test_only");

    @Autowired
    private SettlementImportService importService;

    @Autowired
    private SettlementImportQueryService queryService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private UUID actorId;

    @BeforeEach
    void createActor() {
        actorId = insertIdentityUser();
    }

    @Test
    void atomicallyImportsAndReconcilesEveryRow() {
        UUID matchedPaymentId =
            insertCompletedPayment(
                250L,
                CREATED_AT
            );
        UUID pendingPaymentId =
            insertPendingPayment(500L);
        UUID missingPaymentId = UUID.randomUUID();

        byte[] csv =
            csv(
                row(
                    "settlement-matched",
                    matchedPaymentId,
                    250L,
                    CREATED_AT.plusSeconds(1L)
                ),
                row(
                    "settlement-pending",
                    pendingPaymentId,
                    500L,
                    CREATED_AT.plusSeconds(1L)
                ),
                row(
                    "settlement-missing",
                    missingPaymentId,
                    100L,
                    CREATED_AT.plusSeconds(1L)
                )
            );

        SettlementImportResponse imported =
            importService.importFile(
                actorId,
                "daily.csv",
                csv
            );

        assertThat(imported.existingImport())
            .isFalse();
        assertThat(imported.rowCount())
            .isEqualTo(3);
        assertThat(imported.matchedCount())
            .isEqualTo(1);
        assertThat(imported.discrepancyCount())
            .isEqualTo(2);

        SettlementResultPageResponse page =
            queryService.findResults(
                imported.importId(),
                0,
                100
            );

        assertThat(page.results())
            .extracting(
                SettlementResultResponse::outcome
            )
            .containsExactly(
                "MATCHED",
                "DISCREPANCY",
                "DISCREPANCY"
            );

        assertThat(page.results())
            .extracting(
                SettlementResultResponse
                    ::discrepancyCode
            )
            .containsExactly(
                null,
                "PAYMENT_NOT_COMPLETED",
                "PAYMENT_NOT_FOUND"
            );
    }

    @Test
    void identicalRawBytesReplayTheCompletedImport() {
        UUID paymentId =
            insertCompletedPayment(
                250L,
                CREATED_AT
            );
        byte[] csv =
            csv(
                row(
                    "settlement-replay",
                    paymentId,
                    250L,
                    CREATED_AT
                )
            );

        SettlementImportResponse first =
            importService.importFile(
                actorId,
                "first.csv",
                csv
            );
        SettlementImportResponse replay =
            importService.importFile(
                actorId,
                "renamed.csv",
                csv
            );

        assertThat(replay.importId())
            .isEqualTo(first.importId());
        assertThat(replay.existingImport())
            .isTrue();
        assertThat(replay.originalFilename())
            .isEqualTo("first.csv");

        assertThat(
            jdbcTemplate.queryForObject(
                "SELECT COUNT(*) "
                    + "FROM settlement_record "
                    + "WHERE settlement_import_id = ?",
                Integer.class,
                first.importId()
            )
        )
            .isEqualTo(1);
    }

    @Test
    void secondOtherwiseValidRowLosesThePaymentClaim() {
        UUID paymentId =
            insertCompletedPayment(
                250L,
                CREATED_AT
            );

        SettlementImportResponse imported =
            importService.importFile(
                actorId,
                "duplicate-payment.csv",
                csv(
                    row(
                        "settlement-first",
                        paymentId,
                        250L,
                        CREATED_AT
                    ),
                    row(
                        "settlement-second",
                        paymentId,
                        250L,
                        CREATED_AT
                    )
                )
            );

        SettlementResultPageResponse page =
            queryService.findResults(
                imported.importId(),
                0,
                100
            );

        assertThat(page.results())
            .extracting(
                SettlementResultResponse::outcome
            )
            .containsExactly(
                "MATCHED",
                "DISCREPANCY"
            );
        assertThat(
            page.results()
                .getLast()
                .discrepancyCode()
        )
            .isEqualTo(
                "DUPLICATE_PAYMENT_SETTLEMENT"
            );
    }

    @Test
    void externalIdentifierConflictRollsBackCandidate() {
        UUID firstPaymentId =
            insertCompletedPayment(
                250L,
                CREATED_AT
            );
        UUID secondPaymentId =
            insertCompletedPayment(
                500L,
                CREATED_AT
            );

        importService.importFile(
            actorId,
            "first.csv",
            csv(
                row(
                    "settlement-shared",
                    firstPaymentId,
                    250L,
                    CREATED_AT
                )
            )
        );

        assertThatThrownBy(
            () ->
                importService.importFile(
                    actorId,
                    "second.csv",
                    csv(
                        row(
                            "settlement-shared",
                            secondPaymentId,
                            500L,
                            CREATED_AT
                        )
                    )
                )
        )
            .isInstanceOf(
                SettlementImportConflictException.class
            );

    }

    private UUID insertIdentityUser() {
        UUID userId = UUID.randomUUID();
        String email =
            userId + "@settlement.test";

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
            "settlement-test-password-hash",
            CREATED_AT.atOffset(ZoneOffset.UTC),
            CREATED_AT.atOffset(ZoneOffset.UTC)
        );

        return userId;
    }

    private UUID insertPendingPayment(
        long amountMinorUnits
    ) {
        return insertPayment(
            amountMinorUnits,
            "PENDING",
            null,
            CREATED_AT
        );
    }

    private UUID insertCompletedPayment(
        long amountMinorUnits,
        Instant completedAt
    ) {
        UUID ledgerTransactionId = UUID.randomUUID();

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
            "settlement-test-"
                + ledgerTransactionId,
            completedAt.atOffset(ZoneOffset.UTC),
            "Settlement reconciliation test"
        );

        return insertPayment(
            amountMinorUnits,
            "COMPLETED",
            ledgerTransactionId,
            completedAt
        );
    }

    private UUID insertPayment(
        long amountMinorUnits,
        String status,
        UUID ledgerTransactionId,
        Instant updatedAt
    ) {
        UUID paymentId = UUID.randomUUID();

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
            VALUES (?, ?, ?, ?, ?, 'GBP', ?, ?, NULL,
                NULL, ?, ?, 0)
            """,
            paymentId,
            actorId,
            UUID.randomUUID(),
            UUID.randomUUID(),
            amountMinorUnits,
            status,
            ledgerTransactionId,
            CREATED_AT.atOffset(ZoneOffset.UTC),
            updatedAt.atOffset(ZoneOffset.UTC)
        );

        return paymentId;
    }

    private static byte[] csv(
        String... rows
    ) {
        String content =
            "settlement_record_id,payment_id,"
                + "amount_minor_units,currency,"
                + "settled_at\r\n"
                + String.join("\r\n", rows)
                + "\r\n";

        return content.getBytes(
            StandardCharsets.UTF_8
        );
    }

    private static String row(
        String settlementRecordId,
        UUID paymentId,
        long amountMinorUnits,
        Instant settledAt
    ) {
        return String.join(
            ",",
            settlementRecordId,
            paymentId.toString(),
            Long.toString(amountMinorUnits),
            "GBP",
            settledAt.toString()
        );
    }
}
