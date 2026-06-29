package com.samharrison.payments.ledger;

import java.io.Serial;

public final class InvalidLedgerPostingException
    extends IllegalArgumentException {

    @Serial
    private static final long serialVersionUID = 1L;

    public InvalidLedgerPostingException(
        String message
    ) {
        super(message);
    }

    public InvalidLedgerPostingException(
        String message,
        Throwable cause
    ) {
        super(message, cause);
    }
}