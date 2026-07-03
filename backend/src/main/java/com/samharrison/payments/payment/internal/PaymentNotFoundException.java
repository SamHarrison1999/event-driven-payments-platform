package com.samharrison.payments.payment.internal;

import java.io.Serial;
import java.util.UUID;

final class PaymentNotFoundException
    extends RuntimeException {

    @Serial
    private static final long serialVersionUID = 1L;

    PaymentNotFoundException(
        UUID paymentId
    ) {
        super(
            "Payment "
                + paymentId
                + " was not found."
        );
    }
}
