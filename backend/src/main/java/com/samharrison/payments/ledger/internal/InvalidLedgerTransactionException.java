package com.samharrison.payments.ledger.internal;

import java.io.Serial;

public class InvalidLedgerTransactionException
    extends IllegalArgumentException {

    @Serial
    private static final long serialVersionUID = 1L;

    public InvalidLedgerTransactionException(
        String message
    ) {
        super(message);
    }

    public InvalidLedgerTransactionException(
        String message,
        Throwable cause
    ) {
        super(message, cause);
    }
}