package com.samharrison.payments.reconciliation.internal;

import java.time.Instant;
import java.util.UUID;

public record SettlementResultResponse(
    int rowNumber,
    String settlementRecordId,
    UUID paymentId,
    long amountMinorUnits,
    String currency,
    Instant settledAt,
    String outcome,
    String discrepancyCode,
    Instant reconciledAt
) {
}
