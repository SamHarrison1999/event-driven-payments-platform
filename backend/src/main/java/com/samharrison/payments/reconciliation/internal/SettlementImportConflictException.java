package com.samharrison.payments.reconciliation.internal;

final class SettlementImportConflictException
    extends RuntimeException {

    private static final long serialVersionUID = 1L;

    SettlementImportConflictException(
        String message,
        Throwable cause
    ) {
        super(message, cause);
    }
}
