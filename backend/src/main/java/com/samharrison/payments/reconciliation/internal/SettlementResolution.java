package com.samharrison.payments.reconciliation.internal;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(
    name = "settlement_resolution",
    uniqueConstraints = {
        @UniqueConstraint(
            name =
                "uq_settlement_resolution_discrepancy",
            columnNames = "settlement_discrepancy_id"
        )
    }
)
class SettlementResolution {

    static final int MAX_REASON_LENGTH = 500;

    @Id
    @Column(
        name = "id",
        nullable = false,
        updatable = false
    )
    private UUID id;

    @Column(
        name = "settlement_discrepancy_id",
        nullable = false,
        updatable = false
    )
    private UUID settlementDiscrepancyId;

    @Column(
        name = "actor_identity_user_id",
        nullable = false,
        updatable = false
    )
    private UUID actorIdentityUserId;

    @Enumerated(EnumType.STRING)
    @Column(
        name = "decision",
        nullable = false,
        updatable = false,
        length = 48
    )
    private SettlementResolutionDecision decision;

    @Column(
        name = "reason",
        nullable = false,
        updatable = false,
        length = MAX_REASON_LENGTH
    )
    private String reason;

    @Column(
        name = "discrepancy_version",
        nullable = false,
        updatable = false
    )
    private long discrepancyVersion;

    @Column(
        name = "decided_at",
        nullable = false,
        updatable = false
    )
    private Instant decidedAt;

    protected SettlementResolution() {
        // Required by JPA.
    }

    private SettlementResolution(
        SettlementDiscrepancy discrepancy,
        UUID actorIdentityUserId,
        SettlementResolutionDecision decision,
        String reason,
        Instant decidedAt
    ) {
        SettlementDiscrepancy requiredDiscrepancy =
            Objects.requireNonNull(
                discrepancy,
                "discrepancy must not be null"
            );

        if (
            requiredDiscrepancy.status()
                != SettlementDiscrepancyStatus.OPEN
        ) {
            throw new
                SettlementDiscrepancyLifecycleException(
                    requiredDiscrepancy.id()
                );
        }

        id = UUID.randomUUID();
        settlementDiscrepancyId =
            requiredDiscrepancy.id();
        this.actorIdentityUserId =
            Objects.requireNonNull(
                actorIdentityUserId,
                "actorIdentityUserId must not be null"
            );
        this.decision =
            Objects.requireNonNull(
                decision,
                "decision must not be null"
            );
        this.reason = requireReason(reason);
        discrepancyVersion =
            requiredDiscrepancy.version();
        this.decidedAt =
            Objects.requireNonNull(
                decidedAt,
                "decidedAt must not be null"
            );

        if (
            this.decidedAt.isBefore(
                requiredDiscrepancy.createdAt()
            )
        ) {
            throw new InvalidSettlementResolutionException(
                "decidedAt must not precede "
                    + "discrepancy creation."
            );
        }
    }

    static SettlementResolution decide(
        SettlementDiscrepancy discrepancy,
        UUID actorIdentityUserId,
        SettlementResolutionDecision decision,
        String reason,
        Instant decidedAt
    ) {
        return new SettlementResolution(
            discrepancy,
            actorIdentityUserId,
            decision,
            reason,
            decidedAt
        );
    }

    private static String requireReason(
        String reason
    ) {
        if (reason == null) {
            throw new InvalidSettlementResolutionException(
                "reason must contain between 1 and "
                    + MAX_REASON_LENGTH
                    + " characters"
            );
        }

        String candidate = reason.strip();

        if (
            candidate.isEmpty()
                || candidate.length()
                    > MAX_REASON_LENGTH
        ) {
            throw new InvalidSettlementResolutionException(
                "reason must contain between 1 and "
                    + MAX_REASON_LENGTH
                    + " characters"
            );
        }

        for (
            int index = 0;
            index < candidate.length();
            index++
        ) {
            if (
                Character.isISOControl(
                    candidate.charAt(index)
                )
            ) {
                throw new InvalidSettlementResolutionException(
                    "reason must not contain control "
                        + "characters"
                );
            }
        }

        return candidate;
    }

    UUID id() {
        return id;
    }

    UUID settlementDiscrepancyId() {
        return settlementDiscrepancyId;
    }

    UUID actorIdentityUserId() {
        return actorIdentityUserId;
    }

    SettlementResolutionDecision decision() {
        return decision;
    }

    String reason() {
        return reason;
    }

    long discrepancyVersion() {
        return discrepancyVersion;
    }

    Instant decidedAt() {
        return decidedAt;
    }
}
