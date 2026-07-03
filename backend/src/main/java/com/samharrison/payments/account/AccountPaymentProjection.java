package com.samharrison.payments.account;

import com.samharrison.payments.shared.GbpAmount;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public record AccountPaymentProjection(
    UUID accountId,
    UUID customerId,
    GbpAmount balance,
    Instant updatedAt,
    long version
) {

    public AccountPaymentProjection {
        accountId =
            Objects.requireNonNull(
                accountId,
                "accountId must not be null"
            );

        customerId =
            Objects.requireNonNull(
                customerId,
                "customerId must not be null"
            );

        balance =
            Objects.requireNonNull(
                balance,
                "balance must not be null"
            );

        updatedAt =
            Objects.requireNonNull(
                updatedAt,
                "updatedAt must not be null"
            );

        if (version < 0L) {
            throw new IllegalArgumentException(
                "version must not be negative"
            );
        }
    }
}
