package com.samharrison.payments.payment;

import java.util.Map;

public record PaymentOperationalSummary(
    long submittedCount,
    long terminalCount,
    long completedCount,
    long rejectedCount,
    long failedCount,
    long completedAmountMinorUnits,
    Map<String, Long> rejectionCodeCounts,
    Map<String, Long> failureCodeCounts
) {

    public PaymentOperationalSummary {
        rejectionCodeCounts =
            Map.copyOf(rejectionCodeCounts);
        failureCodeCounts =
            Map.copyOf(failureCodeCounts);
    }
}
