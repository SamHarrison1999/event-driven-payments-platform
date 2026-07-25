package com.samharrison.payments.reconciliation;

import java.time.Instant;
import java.util.UUID;

public record ReconciliationReportRow(
    UUID discrepancyId,
    UUID settlementImportId,
    String settlementRecordId,
    String code,
    String status,
    Instant createdAt,
    String resolutionDecision,
    Instant resolvedAt,
    UUID resolutionActorIdentityUserId
) {
}
