package com.samharrison.payments.reconciliation.internal;

import java.io.Serial;

final class SettlementDiscrepancyVersionRequiredException
    extends RuntimeException {

    @Serial
    private static final long serialVersionUID = 1L;

    SettlementDiscrepancyVersionRequiredException() {
        super(
            "The If-Match header is required to "
                + "resolve a settlement discrepancy."
        );
    }
}
