package com.samharrison.payments.payment.internal;

import java.io.Serial;
import java.util.Objects;

final class PaymentIdempotencyConflictException
    extends RuntimeException {

    @Serial
    private static final long serialVersionUID = 1L;

    private final PaymentReservationResult.Reason reason;

    PaymentIdempotencyConflictException(
        PaymentReservationResult.Reason reason
    ) {
        super(messageFor(reason));

        this.reason =
            Objects.requireNonNull(
                reason,
                "reason must not be null"
            );
    }

    PaymentReservationResult.Reason reason() {
        return reason;
    }

    private static String messageFor(
        PaymentReservationResult.Reason reason
    ) {
        return switch (
            Objects.requireNonNull(
                reason,
                "reason must not be null"
            )
        ) {
            case IDEMPOTENCY_KEY_REUSED ->
                "The idempotency key was already used "
                    + "for a different payment request.";
            case IDEMPOTENCY_REQUEST_IN_PROGRESS ->
                "A payment request with this "
                    + "idempotency key is still processing.";
        };
    }
}
