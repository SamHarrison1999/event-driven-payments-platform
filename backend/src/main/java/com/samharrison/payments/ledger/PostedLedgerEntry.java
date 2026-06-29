package com.samharrison.payments.ledger;

import com.samharrison.payments.shared.GbpAmount;
import java.util.UUID;

public record PostedLedgerEntry(
    UUID id,
    UUID ledgerAccountId,
    LedgerEntrySide side,
    GbpAmount amount,
    int sequence,
    String description
) {
}