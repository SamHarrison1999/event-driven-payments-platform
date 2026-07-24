package com.samharrison.payments.reconciliation.internal;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

record ParsedSettlementRecord(
    int rowNumber,
    String settlementRecordId,
    UUID paymentId,
    long amountMinorUnits,
    String currency,
    Instant settledAt
) {

    ParsedSettlementRecord {
        if (rowNumber < 1) {
            throw new IllegalArgumentException(
                "rowNumber must be positive"
            );
        }

        Objects.requireNonNull(
            settlementRecordId,
            "settlementRecordId must not be null"
        );
        Objects.requireNonNull(
            paymentId,
            "paymentId must not be null"
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
        Objects.requireNonNull(
            settledAt,
            "settledAt must not be null"
        );
    }
}
