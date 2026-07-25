package com.samharrison.payments.reconciliation;

import java.util.Map;

public record ReconciliationOperationalSummary(
    Map<String, Long> discrepancyCodeCounts,
    Map<String, Long> lifecycleStateCounts,
    Map<String, Long> resolutionDecisionCounts,
    Map<String, Long> openAgeBandCounts
) {

    public ReconciliationOperationalSummary {
        discrepancyCodeCounts =
            Map.copyOf(discrepancyCodeCounts);
        lifecycleStateCounts =
            Map.copyOf(lifecycleStateCounts);
        resolutionDecisionCounts =
            Map.copyOf(resolutionDecisionCounts);
        openAgeBandCounts =
            Map.copyOf(openAgeBandCounts);
    }
}
