package com.samharrison.payments.ledger.internal;

import com.samharrison.payments.shared.GbpAmount;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(
    name = "ledger_entry",
    uniqueConstraints = {
        @UniqueConstraint(
            name = "uq_ledger_entry_transaction_sequence",
            columnNames = {
                "transaction_id",
                "entry_sequence"
            }
        )
    },
    indexes = {
        @Index(
            name = "idx_ledger_entry_transaction",
            columnList = "transaction_id,entry_sequence"
        ),
        @Index(
            name = "idx_ledger_entry_account",
            columnList = "ledger_account_id,transaction_id"
        )
    }
)
class LedgerEntryRecord {

    @Id
    @Column(
        name = "id",
        nullable = false,
        updatable = false
    )
    private UUID id;

    @Column(
        name = "transaction_id",
        nullable = false,
        updatable = false
    )
    private UUID transactionId;

    @Column(
        name = "ledger_account_id",
        nullable = false,
        updatable = false
    )
    private UUID ledgerAccountId;

    @Enumerated(EnumType.STRING)
    @Column(
        name = "side",
        nullable = false,
        updatable = false,
        length = 16
    )
    private LedgerSide side;

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
        name = "entry_sequence",
        nullable = false,
        updatable = false
    )
    private int sequence;

    @Column(
        name = "description",
        nullable = false,
        updatable = false,
        length = LedgerEntryDraft.MAX_DESCRIPTION_LENGTH
    )
    private String description;

    protected LedgerEntryRecord() {
        // Required by JPA.
    }

    private LedgerEntryRecord(
        UUID id,
        UUID transactionId,
        UUID ledgerAccountId,
        LedgerSide side,
        long amountMinorUnits,
        String currency,
        int sequence,
        String description
    ) {
        this.id =
            Objects.requireNonNull(
                id,
                "id must not be null"
            );

        this.transactionId =
            Objects.requireNonNull(
                transactionId,
                "transactionId must not be null"
            );

        this.ledgerAccountId =
            Objects.requireNonNull(
                ledgerAccountId,
                "ledgerAccountId must not be null"
            );

        this.side =
            Objects.requireNonNull(
                side,
                "side must not be null"
            );

        this.amountMinorUnits = amountMinorUnits;
        this.currency =
            Objects.requireNonNull(
                currency,
                "currency must not be null"
            );
        this.sequence = sequence;

        this.description =
            Objects.requireNonNull(
                description,
                "description must not be null"
            );
    }

    static LedgerEntryRecord from(
        LedgerEntry entry
    ) {
        LedgerEntry required =
            Objects.requireNonNull(
                entry,
                "entry must not be null"
            );

        return new LedgerEntryRecord(
            required.id(),
            required.transactionId(),
            required.ledgerAccountId(),
            required.side(),
            required.amount().minorUnits(),
            GbpAmount.CURRENCY_CODE,
            required.sequence(),
            required.description()
        );
    }

    UUID id() {
        return id;
    }

    UUID transactionId() {
        return transactionId;
    }

    UUID ledgerAccountId() {
        return ledgerAccountId;
    }

    LedgerSide side() {
        return side;
    }

    GbpAmount amount() {
        return GbpAmount.ofMinorUnits(
            amountMinorUnits
        );
    }

    String currency() {
        return currency;
    }

    int sequence() {
        return sequence;
    }

    String description() {
        return description;
    }
}