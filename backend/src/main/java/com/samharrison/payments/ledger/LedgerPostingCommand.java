package com.samharrison.payments.ledger;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public record LedgerPostingCommand(
    String transactionType,
    String businessReference,
    UUID correctsTransactionId,
    String description,
    List<LedgerPostingEntry> entries
) {

    public LedgerPostingCommand {
        if (transactionType == null) {
            throw new InvalidLedgerPostingException(
                "Ledger posting transaction type "
                    + "is required."
            );
        }

        if (description == null) {
            throw new InvalidLedgerPostingException(
                "Ledger posting description is required."
            );
        }

        if (entries == null) {
            throw new InvalidLedgerPostingException(
                "Ledger posting entries are required."
            );
        }

        List<LedgerPostingEntry> copiedEntries =
            new ArrayList<>(entries.size());

        for (LedgerPostingEntry entry : entries) {
            if (entry == null) {
                throw new InvalidLedgerPostingException(
                    "Ledger posting entries must not "
                        + "contain null values."
                );
            }

            copiedEntries.add(entry);
        }

        entries = List.copyOf(copiedEntries);
    }
}