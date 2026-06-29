package com.samharrison.payments.ledger;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record PostedLedgerTransaction(
    UUID id,
    String transactionType,
    String businessReference,
    UUID correctsTransactionId,
    Instant postedAt,
    String description,
    List<PostedLedgerEntry> entries
) {

    public PostedLedgerTransaction {
        entries = List.copyOf(entries);
    }
}