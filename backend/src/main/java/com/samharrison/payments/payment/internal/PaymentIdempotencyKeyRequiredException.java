package com.samharrison.payments.payment.internal;

import java.io.Serial;

final class PaymentIdempotencyKeyRequiredException
    extends IllegalArgumentException {

    @Serial
    private static final long serialVersionUID = 1L;

    PaymentIdempotencyKeyRequiredException() {
        super("Idempotency-Key header is required.");
    }
}
