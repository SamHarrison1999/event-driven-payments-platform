package com.samharrison.payments.reconciliation.internal;

import java.io.Serial;
import java.util.UUID;

final class SettlementDiscrepancyNotFoundException
    extends RuntimeException {

    @Serial
    private static final long serialVersionUID = 1L;

    private final UUID discrepancyId;

    SettlementDiscrepancyNotFoundException(
        UUID discrepancyId
    ) {
        super(
            "Settlement discrepancy "
                + discrepancyId
                + " was not found."
        );

        this.discrepancyId = discrepancyId;
    }

    UUID discrepancyId() {
        return discrepancyId;
    }
}
