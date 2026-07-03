package com.samharrison.payments.payment.internal;

import com.samharrison.payments.shared.GbpAmount;
import java.time.Instant;
import java.util.UUID;

public record PaymentResponse(
    UUID paymentId,
    UUID sourceAccountId,
    UUID destinationAccountId,
    long amountMinorUnits,
    String currency,
    String status,
    UUID ledgerTransactionId,
    String rejectionReason,
    String failureReason,
    Instant createdAt,
    Instant updatedAt,
    long version
) {

    static PaymentResponse from(
        Payment payment
    ) {
        PaymentRequestData request =
            payment.request();

        return new PaymentResponse(
            payment.id(),
            request.sourceAccountId(),
            request.destinationAccountId(),
            request.amount().minorUnits(),
            GbpAmount.CURRENCY_CODE,
            payment.status().name(),
            payment.ledgerTransactionId(),
            payment.rejectionReason() == null
                ? null
                : payment.rejectionReason().code(),
            payment.failureReason() == null
                ? null
                : payment.failureReason().code(),
            payment.createdAt(),
            payment.updatedAt(),
            payment.version()
        );
    }
}
