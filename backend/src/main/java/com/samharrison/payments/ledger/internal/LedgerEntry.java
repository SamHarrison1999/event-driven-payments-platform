package com.samharrison.payments.ledger.internal;

import com.samharrison.payments.shared.GbpAmount;
import java.util.Objects;
import java.util.UUID;

public record LedgerEntry(
    UUID id,
    UUID transactionId,
    UUID ledgerAccountId,
    LedgerSide side,
    GbpAmount amount,
    int sequence,
    String description
) {

    public LedgerEntry {
        id =
            Objects.requireNonNull(
                id,
                "id must not be null"
            );

        transactionId =
            Objects.requireNonNull(
                transactionId,
                "transactionId must not be null"
            );

        ledgerAccountId =
            Objects.requireNonNull(
                ledgerAccountId,
                "ledgerAccountId must not be null"
            );

        side =
            Objects.requireNonNull(
                side,
                "side must not be null"
            );

        amount =
            Objects.requireNonNull(
                amount,
                "amount must not be null"
            );

        if (!amount.isPositive()) {
            throw new InvalidLedgerTransactionException(
                "Ledger entry amount must be greater than zero."
            );
        }

        if (sequence < 1) {
            throw new InvalidLedgerTransactionException(
                "Ledger entry sequence must be positive."
            );
        }

        description =
            normalizeDescription(description);
    }

    private static String normalizeDescription(
        String rawDescription
    ) {
        if (rawDescription == null) {
            throw new InvalidLedgerTransactionException(
                "Ledger entry description is required."
            );
        }

        String normalized = rawDescription.strip();

        if (normalized.isEmpty()) {
            throw new InvalidLedgerTransactionException(
                "Ledger entry description is required."
            );
        }

        if (
            normalized.length()
                > LedgerEntryDraft.MAX_DESCRIPTION_LENGTH
        ) {
            throw new InvalidLedgerTransactionException(
                "Ledger entry description must not exceed "
                    + LedgerEntryDraft.MAX_DESCRIPTION_LENGTH
                    + " characters."
            );
        }

        boolean containsControlCharacter =
            normalized
                .codePoints()
                .anyMatch(Character::isISOControl);

        if (containsControlCharacter) {
            throw new InvalidLedgerTransactionException(
                "Ledger entry description must not "
                    + "contain control characters."
            );
        }

        return normalized;
    }

    static LedgerEntry from(
        UUID transactionId,
        int sequence,
        LedgerEntryDraft draft
    ) {
        LedgerEntryDraft requiredDraft =
            Objects.requireNonNull(
                draft,
                "draft must not be null"
            );

        return new LedgerEntry(
            UUID.randomUUID(),
            transactionId,
            requiredDraft.ledgerAccountId(),
            requiredDraft.side(),
            requiredDraft.amount(),
            sequence,
            requiredDraft.description()
        );
    }
}