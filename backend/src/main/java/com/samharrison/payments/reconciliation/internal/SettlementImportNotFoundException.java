package com.samharrison.payments.reconciliation.internal;

import java.util.UUID;

final class SettlementImportNotFoundException
    extends RuntimeException {

    private static final long serialVersionUID = 1L;

    SettlementImportNotFoundException(
        UUID importId
    ) {
        super(
            "Settlement import "
                + importId
                + " was not found."
        );
    }
}
