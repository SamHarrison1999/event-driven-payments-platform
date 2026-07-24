package com.samharrison.payments.reconciliation.internal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class SettlementImportTest {

    private static final Instant CREATED_AT =
        Instant.parse("2026-07-24T10:00:00Z");

    @Test
    void createsProcessingImportAndCompletesWithExactCounts() {
        SettlementImport settlementImport =
            processingImport();

        assertThat(settlementImport.status())
            .isEqualTo(
                SettlementImportStatus.PROCESSING
            );
        assertThat(settlementImport.rowCount())
            .isNull();

        settlementImport.complete(
            2,
            1,
            1,
            CREATED_AT.plusSeconds(1L)
        );

        assertThat(settlementImport.status())
            .isEqualTo(
                SettlementImportStatus.COMPLETED
            );
        assertThat(settlementImport.rowCount())
            .isEqualTo(2);
        assertThat(settlementImport.matchedCount())
            .isEqualTo(1);
        assertThat(
            settlementImport.discrepancyCount()
        )
            .isEqualTo(1);
    }

    @Test
    void rejectsInvalidCompletionCountsAndRepeatCompletion() {
        SettlementImport invalidCounts =
            processingImport();

        assertThatThrownBy(
            () ->
                invalidCounts.complete(
                    2,
                    2,
                    1,
                    CREATED_AT
                )
        )
            .isInstanceOf(
                InvalidSettlementImportException.class
            );

        SettlementImport completed =
            processingImport();
        completed.complete(
            1,
            1,
            0,
            CREATED_AT
        );

        assertThatThrownBy(
            () ->
                completed.complete(
                    1,
                    1,
                    0,
                    CREATED_AT
                )
        )
            .isInstanceOf(
                InvalidSettlementImportException.class
            );
    }

    @Test
    void rejectsUnsafeFilenameAndCompletionBeforeCreation() {
        ParsedSettlementFile parsedFile =
            parsedFile();

        assertThatThrownBy(
            () ->
                SettlementImport.processing(
                    parsedFile,
                    "unsafe\nname.csv",
                    UUID.randomUUID(),
                    CREATED_AT
                )
        )
            .isInstanceOf(
                InvalidSettlementImportException.class
            );

        SettlementImport settlementImport =
            processingImport();

        assertThatThrownBy(
            () ->
                settlementImport.complete(
                    1,
                    1,
                    0,
                    CREATED_AT.minusSeconds(1L)
                )
        )
            .isInstanceOf(
                InvalidSettlementImportException.class
            );
    }

    private static SettlementImport processingImport() {
        return SettlementImport.processing(
            parsedFile(),
            "settlement.csv",
            UUID.randomUUID(),
            CREATED_AT
        );
    }

    private static ParsedSettlementFile parsedFile() {
        return new ParsedSettlementFile(
            "0".repeat(64),
            100,
            List.of(
                new ParsedSettlementRecord(
                    1,
                    "record-1",
                    UUID.randomUUID(),
                    100L,
                    "GBP",
                    CREATED_AT
                )
            )
        );
    }
}
