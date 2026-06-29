package com.samharrison.payments.ledger;

import com.samharrison.payments.shared.GbpAmount;
import java.util.UUID;

public record LedgerBalanceVerification(
    UUID accountId,
    GbpAmount snapshotBalance,
    GbpAmount totalDebits,
    GbpAmount totalCredits,
    boolean consistent
) {
}