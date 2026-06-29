package com.samharrison.payments.ledger;

import com.samharrison.payments.shared.GbpAmount;
import java.time.Instant;
import java.util.UUID;

public record LedgerAccountEntry(
    UUID entryId,
    UUID transactionId,
    String transactionType,
    String businessReference,
    Instant postedAt,
    LedgerEntrySide side,
    GbpAmount amount,
    int sequence,
    String description
) {
}