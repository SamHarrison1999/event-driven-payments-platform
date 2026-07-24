package com.samharrison.payments.reconciliation.internal;

final class InvalidSettlementImportException
    extends RuntimeException {

    private static final long serialVersionUID = 1L;

    InvalidSettlementImportException(
        String message
    ) {
        super(message);
    }
}
