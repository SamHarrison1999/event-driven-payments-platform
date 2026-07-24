package com.samharrison.payments.reconciliation.internal;

import java.io.Serial;
import java.util.UUID;

final class SettlementDiscrepancyLifecycleException
    extends RuntimeException {

    @Serial
    private static final long serialVersionUID = 1L;

    SettlementDiscrepancyLifecycleException(
        UUID discrepancyId
    ) {
        super(
            "Settlement discrepancy "
                + discrepancyId
                + " is already resolved."
        );
    }
}
