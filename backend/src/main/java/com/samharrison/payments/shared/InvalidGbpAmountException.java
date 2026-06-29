package com.samharrison.payments.shared;

import java.io.Serial;

public final class InvalidGbpAmountException
    extends IllegalArgumentException {

    @Serial
    private static final long serialVersionUID = 1L;

    public InvalidGbpAmountException(
        String message
    ) {
        super(message);
    }

    public InvalidGbpAmountException(
        String message,
        Throwable cause
    ) {
        super(message, cause);
    }
}