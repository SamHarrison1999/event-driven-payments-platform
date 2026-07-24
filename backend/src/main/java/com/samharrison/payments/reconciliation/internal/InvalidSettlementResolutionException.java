package com.samharrison.payments.reconciliation.internal;

import java.io.Serial;

final class InvalidSettlementResolutionException
    extends RuntimeException {

    @Serial
    private static final long serialVersionUID = 1L;

    InvalidSettlementResolutionException(
        String message
    ) {
        super(message);
    }
}
