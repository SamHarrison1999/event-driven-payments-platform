package com.samharrison.payments.reconciliation.internal;

import java.io.Serial;

final class InvalidSettlementDiscrepancyVersionException
    extends RuntimeException {

    @Serial
    private static final long serialVersionUID = 1L;

    InvalidSettlementDiscrepancyVersionException(
        String rawHeader
    ) {
        super(
            "The If-Match header must contain one "
                + "strong discrepancy-version ETag, "
                + "for example \"0\". Received: "
                + String.valueOf(rawHeader)
        );
    }
}
