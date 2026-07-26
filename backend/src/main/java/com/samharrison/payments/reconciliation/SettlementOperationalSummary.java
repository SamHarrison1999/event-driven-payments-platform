package com.samharrison.payments.reconciliation;

import java.util.Map;

public record SettlementOperationalSummary(
    long acceptedImportCount,
    long acceptedRowCount,
    long matchedCount,
    long discrepancyCount,
    Map<String, Long> importOutcomeCounts
) {

    public SettlementOperationalSummary {
        importOutcomeCounts =
            Map.copyOf(importOutcomeCounts);
    }
}
