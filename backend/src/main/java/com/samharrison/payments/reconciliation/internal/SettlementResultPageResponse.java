package com.samharrison.payments.reconciliation.internal;

import java.util.List;

public record SettlementResultPageResponse(
    List<SettlementResultResponse> results,
    Integer nextAfterRowNumber
) {

    public SettlementResultPageResponse {
        results = List.copyOf(results);
    }
}
