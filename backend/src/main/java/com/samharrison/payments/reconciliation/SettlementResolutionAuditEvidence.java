package com.samharrison.payments.reconciliation;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public record SettlementResolutionAuditEvidence(
    UUID resolutionId,
    UUID discrepancyId,
    UUID actorIdentityUserId,
    String decision,
    long discrepancyVersion,
    Instant decidedAt
) {

    public SettlementResolutionAuditEvidence {
        resolutionId =
            Objects.requireNonNull(
                resolutionId,
                "resolutionId must not be null"
            );
        discrepancyId =
            Objects.requireNonNull(
                discrepancyId,
                "discrepancyId must not be null"
            );
        actorIdentityUserId =
            Objects.requireNonNull(
                actorIdentityUserId,
                "actorIdentityUserId must not be null"
            );
        decision =
            Objects.requireNonNull(
                decision,
                "decision must not be null"
            );
        decidedAt =
            Objects.requireNonNull(
                decidedAt,
                "decidedAt must not be null"
            );

        if (discrepancyVersion < 0) {
            throw new IllegalArgumentException(
                "discrepancyVersion must not be negative"
            );
        }
    }
}
