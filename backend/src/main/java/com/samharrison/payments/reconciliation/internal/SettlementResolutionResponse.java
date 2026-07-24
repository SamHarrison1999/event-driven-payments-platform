package com.samharrison.payments.reconciliation.internal;

import java.time.Instant;
import java.util.UUID;

public record SettlementResolutionResponse(
    UUID resolutionId,
    UUID actorIdentityUserId,
    String decision,
    String reason,
    long discrepancyVersion,
    Instant decidedAt
) {
}
