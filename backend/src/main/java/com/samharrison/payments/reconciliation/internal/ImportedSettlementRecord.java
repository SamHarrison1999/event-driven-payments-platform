package com.samharrison.payments.reconciliation.internal;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(
    name = "settlement_record",
    uniqueConstraints = {
        @UniqueConstraint(
            name =
                "uq_settlement_record_external_id",
            columnNames = "settlement_record_id"
        ),
        @UniqueConstraint(
            name = "uq_settlement_record_import_row",
            columnNames = {
                "settlement_import_id",
                "row_number"
            }
        )
    },
    indexes = {
        @Index(
            name = "idx_settlement_record_import",
            columnList =
                "settlement_import_id,row_number"
        ),
        @Index(
            name = "idx_settlement_record_payment",
            columnList = "payment_id,id"
        )
    }
)
class ImportedSettlementRecord {

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
        name = "row_number",
        nullable = false,
        updatable = false
    )
    private int rowNumber;

    @Column(
        name = "settlement_record_id",
        nullable = false,
        updatable = false,
        length = 128
    )
    private String settlementRecordId;

    @Column(
        name = "payment_id",
        nullable = false,
        updatable = false
    )
    private UUID paymentId;

    @Column(
        name = "amount_minor_units",
        nullable = false,
        updatable = false
    )
    private long amountMinorUnits;

    @Column(
        name = "currency",
        nullable = false,
        updatable = false,
        length = 3
    )
    private String currency;

    @Column(
        name = "settled_at",
        nullable = false,
        updatable = false
    )
    private Instant settledAt;

    protected ImportedSettlementRecord() {
        // Required by JPA.
    }

    private ImportedSettlementRecord(
        SettlementImport settlementImport,
        ParsedSettlementRecord source
    ) {
        SettlementImport requiredImport =
            Objects.requireNonNull(
                settlementImport,
                "settlementImport must not be null"
            );
        ParsedSettlementRecord requiredSource =
            Objects.requireNonNull(
                source,
                "source must not be null"
            );

        id = UUID.randomUUID();
        settlementImportId = requiredImport.id();
        rowNumber = requiredSource.rowNumber();
        settlementRecordId =
            requiredSource.settlementRecordId();
        paymentId = requiredSource.paymentId();
        amountMinorUnits =
            requiredSource.amountMinorUnits();
        currency = requiredSource.currency();
        settledAt = requiredSource.settledAt();
    }

    static ImportedSettlementRecord from(
        SettlementImport settlementImport,
        ParsedSettlementRecord source
    ) {
        return new ImportedSettlementRecord(
            settlementImport,
            source
        );
    }

    UUID id() {
        return id;
    }

    UUID settlementImportId() {
        return settlementImportId;
    }

    int rowNumber() {
        return rowNumber;
    }

    String settlementRecordId() {
        return settlementRecordId;
    }

    UUID paymentId() {
        return paymentId;
    }

    long amountMinorUnits() {
        return amountMinorUnits;
    }

    String currency() {
        return currency;
    }

    Instant settledAt() {
        return settledAt;
    }
}
