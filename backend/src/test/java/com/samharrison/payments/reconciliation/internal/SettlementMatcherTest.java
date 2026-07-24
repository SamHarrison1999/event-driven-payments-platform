package com.samharrison.payments.reconciliation.internal;

import static org.assertj.core.api.Assertions.assertThat;

import com.samharrison.payments.payment.PaymentReconciliationSnapshot;
import com.samharrison.payments.payment.PaymentReconciliationStatus;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class SettlementMatcherTest {

    private static final UUID PAYMENT_ID =
        UUID.fromString(
            "10000000-0000-0000-0000-000000000001"
        );

    private static final Instant COMPLETED_AT =
        Instant.parse("2026-07-24T10:00:00Z");

    private SettlementMatcher matcher;

    @BeforeEach
    void setUp() {
        matcher = new SettlementMatcher();
    }

    @Test
    void appliesTheDocumentedPriorityOrder() {
        ImportedSettlementRecord mismatchedRecord =
            record(
                999L,
                "USD",
                COMPLETED_AT.minusSeconds(1L)
            );

        assertThat(
            matcher
                .evaluate(mismatchedRecord, null)
                .discrepancyCode()
        )
            .isEqualTo(
                SettlementDiscrepancyCode
                    .PAYMENT_NOT_FOUND
            );

        assertThat(
            matcher
                .evaluate(
                    mismatchedRecord,
                    snapshot(
                        PaymentReconciliationStatus
                            .PENDING,
                        250L,
                        "GBP",
                        null
                    )
                )
                .discrepancyCode()
        )
            .isEqualTo(
                SettlementDiscrepancyCode
                    .PAYMENT_NOT_COMPLETED
            );

        assertThat(
            matcher
                .evaluate(
                    mismatchedRecord,
                    completedSnapshot(
                        250L,
                        "GBP"
                    )
                )
                .discrepancyCode()
        )
            .isEqualTo(
                SettlementDiscrepancyCode
                    .CURRENCY_MISMATCH
            );
    }

    @Test
    void detectsAmountAndSettlementTimeMismatches() {
        assertThat(
            matcher
                .evaluate(
                    record(
                        251L,
                        "GBP",
                        COMPLETED_AT
                    ),
                    completedSnapshot(
                        250L,
                        "GBP"
                    )
                )
                .discrepancyCode()
        )
            .isEqualTo(
                SettlementDiscrepancyCode
                    .AMOUNT_MISMATCH
            );

        assertThat(
            matcher
                .evaluate(
                    record(
                        250L,
                        "GBP",
                        COMPLETED_AT.minusNanos(1L)
                    ),
                    completedSnapshot(
                        250L,
                        "GBP"
                    )
                )
                .discrepancyCode()
        )
            .isEqualTo(
                SettlementDiscrepancyCode
                    .SETTLED_BEFORE_COMPLETION
            );
    }

    @Test
    void acceptsSettlementAtOrAfterCompletion() {
        ReconciliationDecision decision =
            matcher.evaluate(
                record(
                    250L,
                    "GBP",
                    COMPLETED_AT
                ),
                completedSnapshot(
                    250L,
                    "GBP"
                )
            );

        assertThat(decision.outcome())
            .isEqualTo(
                SettlementResultOutcome.MATCHED
            );
        assertThat(decision.discrepancyCode())
            .isNull();
    }

    private static ImportedSettlementRecord record(
        long amountMinorUnits,
        String currency,
        Instant settledAt
    ) {
        ParsedSettlementRecord parsedRecord =
            new ParsedSettlementRecord(
                1,
                "settlement-1",
                PAYMENT_ID,
                amountMinorUnits,
                currency,
                settledAt
            );

        ParsedSettlementFile parsedFile =
            new ParsedSettlementFile(
                "a".repeat(64),
                100,
                java.util.List.of(parsedRecord)
            );

        SettlementImport settlementImport =
            SettlementImport.processing(
                parsedFile,
                "settlement.csv",
                UUID.randomUUID(),
                COMPLETED_AT
            );

        return ImportedSettlementRecord.from(
            settlementImport,
            parsedRecord
        );
    }

    private static PaymentReconciliationSnapshot
        completedSnapshot(
            long amountMinorUnits,
            String currency
        ) {
        return snapshot(
            PaymentReconciliationStatus.COMPLETED,
            amountMinorUnits,
            currency,
            COMPLETED_AT
        );
    }

    private static PaymentReconciliationSnapshot snapshot(
        PaymentReconciliationStatus status,
        long amountMinorUnits,
        String currency,
        Instant completedAt
    ) {
        return new PaymentReconciliationSnapshot(
            PAYMENT_ID,
            status,
            amountMinorUnits,
            currency,
            completedAt,
            completedAt == null
                ? null
                : UUID.randomUUID()
        );
    }
}
