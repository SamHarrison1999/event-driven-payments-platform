package com.samharrison.payments.payment.internal;

public final class InvalidPaymentException
    extends IllegalArgumentException {

    private static final long serialVersionUID = 1L;

    public InvalidPaymentException(
        String message
    ) {
        super(message);
    }
}
