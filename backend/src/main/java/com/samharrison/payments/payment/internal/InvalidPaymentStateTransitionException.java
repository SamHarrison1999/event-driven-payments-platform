package com.samharrison.payments.payment.internal;

public final class InvalidPaymentStateTransitionException
    extends IllegalStateException {

    private static final long serialVersionUID = 1L;

    public InvalidPaymentStateTransitionException(
        String message
    ) {
        super(message);
    }
}
