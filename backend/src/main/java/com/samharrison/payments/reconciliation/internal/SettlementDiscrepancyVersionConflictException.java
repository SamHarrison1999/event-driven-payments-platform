package com.samharrison.payments.reconciliation.internal;

import java.io.Serial;
import java.util.UUID;

final class SettlementDiscrepancyVersionConflictException
    extends RuntimeException {

    @Serial
    private static final long serialVersionUID = 1L;

    private final UUID discrepancyId;
    private final long expectedVersion;
    private final long actualVersion;

    SettlementDiscrepancyVersionConflictException(
        UUID discrepancyId,
        long expectedVersion,
        long actualVersion
    ) {
        super(
            "Settlement discrepancy "
                + discrepancyId
                + " has version "
                + actualVersion
                + ", not the expected version "
                + expectedVersion
                + "."
        );

        this.discrepancyId = discrepancyId;
        this.expectedVersion = expectedVersion;
        this.actualVersion = actualVersion;
    }

    UUID discrepancyId() {
        return discrepancyId;
    }

    long expectedVersion() {
        return expectedVersion;
    }

    long actualVersion() {
        return actualVersion;
    }
}
