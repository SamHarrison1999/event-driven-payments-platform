package com.samharrison.payments.payment.internal;

import com.samharrison.payments.shared.GbpAmount;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.util.UUID;

public record PaymentCreateRequest(
    @NotNull(
        message = "Source account id is required."
    )
    UUID sourceAccountId,

    @NotNull(
        message = "Destination account id is required."
    )
    UUID destinationAccountId,

    @NotNull(
        message = "Payment amount is required."
    )
    @Positive(
        message = "Payment amount must be greater than zero."
    )
    Long amountMinorUnits
) {

    PaymentRequestData toDomain() {
        return new PaymentRequestData(
            sourceAccountId,
            destinationAccountId,
            GbpAmount.ofMinorUnits(
                amountMinorUnits
            )
        );
    }
}
