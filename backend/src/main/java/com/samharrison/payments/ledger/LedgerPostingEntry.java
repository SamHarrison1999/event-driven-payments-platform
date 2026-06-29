package com.samharrison.payments.ledger;

import com.samharrison.payments.shared.GbpAmount;
import java.util.Objects;
import java.util.UUID;

public record LedgerPostingEntry(
    UUID ledgerAccountId,
    LedgerEntrySide side,
    GbpAmount amount,
    String description
) {

    public LedgerPostingEntry {
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
            throw new InvalidLedgerPostingException(
                "Ledger posting amount must be "
                    + "greater than zero."
            );
        }

        if (description == null) {
            throw new InvalidLedgerPostingException(
                "Ledger posting entry description "
                    + "is required."
            );
        }
    }
}