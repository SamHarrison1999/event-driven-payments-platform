package com.samharrison.payments.ledger.internal;

import com.samharrison.payments.shared.GbpAmount;
import java.util.Objects;
import java.util.UUID;

public record LedgerEntryDraft(
    UUID ledgerAccountId,
    LedgerSide side,
    GbpAmount amount,
    String description
) {

    public static final int MAX_DESCRIPTION_LENGTH = 200;

    public LedgerEntryDraft {
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

        description = normalizeDescription(description);
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
                > MAX_DESCRIPTION_LENGTH
        ) {
            throw new InvalidLedgerTransactionException(
                "Ledger entry description must not exceed "
                    + MAX_DESCRIPTION_LENGTH
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
}