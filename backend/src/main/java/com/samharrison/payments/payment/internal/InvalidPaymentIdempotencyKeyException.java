package com.samharrison.payments.payment.internal;

import java.io.Serial;

final class InvalidPaymentIdempotencyKeyException
    extends IllegalArgumentException {

    @Serial
    private static final long serialVersionUID = 1L;

    InvalidPaymentIdempotencyKeyException(
        String message
    ) {
        super(message);
    }
}
