package com.samharrison.payments.reconciliation.internal;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record SettlementDiscrepancyPageResponse(
    List<SettlementDiscrepancyResponse> discrepancies,
    Instant nextAfterCreatedAt,
    UUID nextAfterId
) {

    public SettlementDiscrepancyPageResponse {
        discrepancies = List.copyOf(discrepancies);
    }
}
