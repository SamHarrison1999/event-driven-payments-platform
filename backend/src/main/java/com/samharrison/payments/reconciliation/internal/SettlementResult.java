package com.samharrison.payments.reconciliation.internal;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(
    name = "settlement_result",
    uniqueConstraints = {
        @UniqueConstraint(
            name = "uq_settlement_result_record",
            columnNames = "settlement_record_id"
        ),
        @UniqueConstraint(
            name = "uq_settlement_result_import_row",
            columnNames = {
                "settlement_import_id",
                "row_number"
            }
        )
    },
    indexes = {
        @Index(
            name = "idx_settlement_result_import",
            columnList =
                "settlement_import_id,row_number"
        ),
        @Index(
            name = "idx_settlement_result_outcome",
            columnList =
                "settlement_import_id,outcome,row_number"
        )
    }
)
class SettlementResult {

    @Id
    @Column(
        name = "id",
        nullable = false,
        updatable = false
    )
    private UUID id;

    @Column(
        name = "settlement_import_id",
        nullable = false,
        updatable = false
    )
    private UUID settlementImportId;

    @Column(
        name = "settlement_record_id",
        nullable = false,
        updatable = false
    )
    private UUID settlementRecordId;

    @Column(
        name = "row_number",
        nullable = false,
        updatable = false
    )
    private int rowNumber;

    @Enumerated(EnumType.STRING)
    @Column(
        name = "outcome",
        nullable = false,
        updatable = false,
        length = 16
    )
    private SettlementResultOutcome outcome;

    @Enumerated(EnumType.STRING)
    @Column(
        name = "discrepancy_code",
        updatable = false,
        length = 64
    )
    private SettlementDiscrepancyCode discrepancyCode;

    @Column(
        name = "reconciled_at",
        nullable = false,
        updatable = false
    )
    private Instant reconciledAt;

    protected SettlementResult() {
        // Required by JPA.
    }

    private SettlementResult(
        ImportedSettlementRecord record,
        ReconciliationDecision decision,
        Instant reconciledAt
    ) {
        ImportedSettlementRecord requiredRecord =
            Objects.requireNonNull(
                record,
                "record must not be null"
            );
        ReconciliationDecision requiredDecision =
            Objects.requireNonNull(
                decision,
                "decision must not be null"
            );

        id = UUID.randomUUID();
        settlementImportId =
            requiredRecord.settlementImportId();
        settlementRecordId = requiredRecord.id();
        rowNumber = requiredRecord.rowNumber();
        outcome = requiredDecision.outcome();
        discrepancyCode =
            requiredDecision.discrepancyCode();
        this.reconciledAt =
            Objects.requireNonNull(
                reconciledAt,
                "reconciledAt must not be null"
            );
    }

    static SettlementResult from(
        ImportedSettlementRecord record,
        ReconciliationDecision decision,
        Instant reconciledAt
    ) {
        return new SettlementResult(
            record,
            decision,
            reconciledAt
        );
    }

    UUID id() {
        return id;
    }

    UUID settlementImportId() {
        return settlementImportId;
    }

    UUID settlementRecordId() {
        return settlementRecordId;
    }

    int rowNumber() {
        return rowNumber;
    }

    SettlementResultOutcome outcome() {
        return outcome;
    }

    SettlementDiscrepancyCode discrepancyCode() {
        return discrepancyCode;
    }

    Instant reconciledAt() {
        return reconciledAt;
    }
}
