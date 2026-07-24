package com.samharrison.payments.payment;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public record PaymentReconciliationSnapshot(
    UUID paymentId,
    PaymentReconciliationStatus status,
    long amountMinorUnits,
    String currency,
    Instant completedAt,
    UUID ledgerTransactionId
) {

    public PaymentReconciliationSnapshot {
        Objects.requireNonNull(
            paymentId,
            "paymentId must not be null"
        );
        Objects.requireNonNull(
            status,
            "status must not be null"
        );

        if (amountMinorUnits <= 0L) {
            throw new IllegalArgumentException(
                "amountMinorUnits must be positive"
            );
        }

        Objects.requireNonNull(
            currency,
            "currency must not be null"
        );

        if (status == PaymentReconciliationStatus.COMPLETED) {
            Objects.requireNonNull(
                completedAt,
                "completedAt must not be null "
                    + "for a completed payment"
            );
            Objects.requireNonNull(
                ledgerTransactionId,
                "ledgerTransactionId must not be null "
                    + "for a completed payment"
            );
        } else if (
            completedAt != null
                || ledgerTransactionId != null
        ) {
            throw new IllegalArgumentException(
                "Only a completed payment may expose "
                    + "completion evidence"
            );
        }
    }
}
