package com.samharrison.payments.reconciliation.internal;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(
    name = "settlement_discrepancy",
    uniqueConstraints = {
        @UniqueConstraint(
            name = "uq_settlement_discrepancy_result",
            columnNames = "settlement_result_id"
        ),
        @UniqueConstraint(
            name = "uq_settlement_discrepancy_record",
            columnNames = "settlement_record_id"
        )
    },
    indexes = {
        @Index(
            name = "idx_settlement_discrepancy_queue",
            columnList = "status,created_at,id"
        ),
        @Index(
            name = "idx_settlement_discrepancy_import",
            columnList = "settlement_import_id,id"
        )
    }
)
class SettlementDiscrepancy {

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
        name = "settlement_result_id",
        nullable = false,
        updatable = false
    )
    private UUID settlementResultId;

    @Column(
        name = "settlement_record_id",
        nullable = false,
        updatable = false
    )
    private UUID settlementRecordId;

    @Enumerated(EnumType.STRING)
    @Column(
        name = "code",
        nullable = false,
        updatable = false,
        length = 64
    )
    private SettlementDiscrepancyCode code;

    @Enumerated(EnumType.STRING)
    @Column(
        name = "status",
        nullable = false,
        length = 16
    )
    private SettlementDiscrepancyStatus status;

    @Column(
        name = "created_at",
        nullable = false,
        updatable = false
    )
    private Instant createdAt;

    @Version
    @Column(
        name = "version",
        nullable = false
    )
    private long version;

    protected SettlementDiscrepancy() {
        // Required by JPA.
    }

    private SettlementDiscrepancy(
        SettlementResult result,
        Instant createdAt
    ) {
        SettlementResult requiredResult =
            Objects.requireNonNull(
                result,
                "result must not be null"
            );

        if (
            requiredResult.outcome()
                != SettlementResultOutcome.DISCREPANCY
        ) {
            throw new IllegalArgumentException(
                "Only a discrepancy result may create "
                    + "a discrepancy."
            );
        }

        id = UUID.randomUUID();
        settlementImportId =
            requiredResult.settlementImportId();
        settlementResultId = requiredResult.id();
        settlementRecordId =
            requiredResult.settlementRecordId();
        code =
            Objects.requireNonNull(
                requiredResult.discrepancyCode(),
                "result discrepancyCode must not be null"
            );
        status = SettlementDiscrepancyStatus.OPEN;
        this.createdAt =
            Objects.requireNonNull(
                createdAt,
                "createdAt must not be null"
            );
    }

    static SettlementDiscrepancy open(
        SettlementResult result,
        Instant createdAt
    ) {
        return new SettlementDiscrepancy(
            result,
            createdAt
        );
    }

    UUID id() {
        return id;
    }

    UUID settlementImportId() {
        return settlementImportId;
    }

    UUID settlementResultId() {
        return settlementResultId;
    }

    UUID settlementRecordId() {
        return settlementRecordId;
    }

    SettlementDiscrepancyCode code() {
        return code;
    }

    SettlementDiscrepancyStatus status() {
        return status;
    }

    Instant createdAt() {
        return createdAt;
    }

    long version() {
        return version;
    }
}
