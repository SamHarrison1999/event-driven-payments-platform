package com.samharrison.payments.payment.internal;

final class PaymentIdempotencyHeader {

    static final String NAME = "Idempotency-Key";

    private PaymentIdempotencyHeader() {
    }

    static IdempotencyKey parse(
        String rawValue
    ) {
        if (rawValue == null) {
            throw new PaymentIdempotencyKeyRequiredException();
        }

        try {
            return IdempotencyKey.of(rawValue);
        } catch (InvalidPaymentException exception) {
            throw new InvalidPaymentIdempotencyKeyException(
                exception.getMessage()
            );
        }
    }
}
