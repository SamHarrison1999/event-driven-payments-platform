package com.samharrison.payments.account.internal;

import com.samharrison.payments.shared.GbpAmount;
import java.time.Instant;
import java.util.UUID;

public record AccountSnapshot(
    UUID id,
    UUID customerId,
    AccountCurrency currency,
    GbpAmount balance,
    AccountStatus status,
    Instant createdAt,
    Instant updatedAt,
    long version
) {

    static AccountSnapshot from(
        CustomerAccount account
    ) {
        return new AccountSnapshot(
            account.id(),
            account.customerId(),
            account.currency(),
            account.balance(),
            account.status(),
            account.createdAt(),
            account.updatedAt(),
            account.version()
        );
    }
}