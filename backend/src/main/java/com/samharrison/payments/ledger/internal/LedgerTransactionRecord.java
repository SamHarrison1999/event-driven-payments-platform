package com.samharrison.payments.ledger.internal;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(
    name = "ledger_transaction",
    indexes = {
        @Index(
            name = "idx_ledger_transaction_posted",
            columnList = "posted_at,id"
        ),
        @Index(
            name = "idx_ledger_transaction_reference",
            columnList = "business_reference"
        ),
        @Index(
            name = "idx_ledger_transaction_correction",
            columnList = "corrects_transaction_id"
        )
    }
)
class LedgerTransactionRecord {

    @Id
    @Column(
        name = "id",
        nullable = false,
        updatable = false
    )
    private UUID id;

    @Column(
        name = "transaction_type",
        nullable = false,
        updatable = false,
        length = LedgerTransactionType.MAX_LENGTH
    )
    private String transactionType;

    @Column(
        name = "business_reference",
        updatable = false,
        length = LedgerTransaction.MAX_REFERENCE_LENGTH
    )
    private String businessReference;

    @Column(
        name = "corrects_transaction_id",
        updatable = false
    )
    private UUID correctsTransactionId;

    @Column(
        name = "posted_at",
        nullable = false,
        updatable = false
    )
    private Instant postedAt;

    @Column(
        name = "description",
        nullable = false,
        updatable = false,
        length = LedgerTransaction.MAX_DESCRIPTION_LENGTH
    )
    private String description;

    protected LedgerTransactionRecord() {
        // Required by JPA.
    }

    private LedgerTransactionRecord(
        UUID id,
        String transactionType,
        String businessReference,
        UUID correctsTransactionId,
        Instant postedAt,
        String description
    ) {
        this.id =
            Objects.requireNonNull(
                id,
                "id must not be null"
            );

        this.transactionType =
            Objects.requireNonNull(
                transactionType,
                "transactionType must not be null"
            );

        this.businessReference = businessReference;
        this.correctsTransactionId =
            correctsTransactionId;

        this.postedAt =
            Objects.requireNonNull(
                postedAt,
                "postedAt must not be null"
            );

        this.description =
            Objects.requireNonNull(
                description,
                "description must not be null"
            );
    }

    static LedgerTransactionRecord from(
        LedgerTransaction transaction
    ) {
        LedgerTransaction required =
            Objects.requireNonNull(
                transaction,
                "transaction must not be null"
            );

        return new LedgerTransactionRecord(
            required.id(),
            required.type().value(),
            required.reference(),
            required.correctsTransactionId(),
            required.postedAt(),
            required.description()
        );
    }

    UUID id() {
        return id;
    }

    String transactionType() {
        return transactionType;
    }

    String businessReference() {
        return businessReference;
    }

    UUID correctsTransactionId() {
        return correctsTransactionId;
    }

    Instant postedAt() {
        return postedAt;
    }

    String description() {
        return description;
    }
}