package com.samharrison.payments.reconciliation;

import java.time.Instant;
import java.util.UUID;

public record SettlementReportRow(
    UUID settlementImportId,
    int rowNumber,
    String settlementRecordId,
    UUID paymentId,
    long amountMinorUnits,
    String currency,
    Instant settledAt,
    String outcome,
    String discrepancyCode,
    Instant importCompletedAt
) {
}
