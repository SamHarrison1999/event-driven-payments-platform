package com.samharrison.payments.reconciliation.internal;

import java.time.Instant;
import java.util.UUID;

public record SettlementDiscrepancyResponse(
    UUID discrepancyId,
    UUID importId,
    int rowNumber,
    String settlementRecordId,
    UUID paymentId,
    long amountMinorUnits,
    String currency,
    Instant settledAt,
    String code,
    String status,
    Instant createdAt,
    long version,
    SettlementResolutionResponse resolution
) {
}
