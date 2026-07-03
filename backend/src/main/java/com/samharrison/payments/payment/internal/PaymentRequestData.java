package com.samharrison.payments.payment.internal;

import com.samharrison.payments.shared.GbpAmount;
import java.util.Objects;
import java.util.UUID;

public record PaymentRequestData(
    UUID sourceAccountId,
    UUID destinationAccountId,
    GbpAmount amount
) {

    public PaymentRequestData {
        sourceAccountId =
            Objects.requireNonNull(
                sourceAccountId,
                "sourceAccountId must not be null"
            );

        destinationAccountId =
            Objects.requireNonNull(
                destinationAccountId,
                "destinationAccountId must not be null"
            );

        amount =
            Objects.requireNonNull(
                amount,
                "amount must not be null"
            );

        if (
            sourceAccountId.equals(
                destinationAccountId
            )
        ) {
            throw new InvalidPaymentException(
                "Source and destination accounts "
                    + "must be different."
            );
        }

        if (!amount.isPositive()) {
            throw new InvalidPaymentException(
                "Payment amount must be greater "
                    + "than zero."
            );
        }
    }
}
