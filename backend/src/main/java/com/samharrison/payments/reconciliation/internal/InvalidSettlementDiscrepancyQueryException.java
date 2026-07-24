package com.samharrison.payments.reconciliation.internal;

import java.io.Serial;

final class InvalidSettlementDiscrepancyQueryException
    extends RuntimeException {

    @Serial
    private static final long serialVersionUID = 1L;

    InvalidSettlementDiscrepancyQueryException() {
        super(
            "afterCreatedAt and afterId must either "
                + "both be supplied or both be omitted."
        );
    }
}
