package com.samharrison.payments.payment.internal;

import java.util.Objects;
import java.util.UUID;

sealed interface PaymentReservationResult
    permits PaymentReservationResult.Acquired,
        PaymentReservationResult.Replay,
        PaymentReservationResult.Conflict {

    record Acquired(
        UUID paymentId,
        UUID ownerToken
    ) implements PaymentReservationResult {

        public Acquired {
            paymentId =
                Objects.requireNonNull(
                    paymentId,
                    "paymentId must not be null"
                );

            ownerToken =
                Objects.requireNonNull(
                    ownerToken,
                    "ownerToken must not be null"
                );
        }
    }

    record Replay(
        StoredPaymentResponse response
    ) implements PaymentReservationResult {

        public Replay {
            response =
                Objects.requireNonNull(
                    response,
                    "response must not be null"
                );
        }
    }

    record Conflict(
        Reason reason
    ) implements PaymentReservationResult {

        public Conflict {
            reason =
                Objects.requireNonNull(
                    reason,
                    "reason must not be null"
                );
        }
    }

    enum Reason {
        IDEMPOTENCY_KEY_REUSED,
        IDEMPOTENCY_REQUEST_IN_PROGRESS
    }
}
