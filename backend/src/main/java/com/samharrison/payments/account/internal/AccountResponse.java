package com.samharrison.payments.account.internal;

import java.time.Instant;
import java.util.UUID;

public record AccountResponse(
    UUID id,
    UUID customerId,
    AccountCurrency currency,
    long balanceMinorUnits,
    AccountStatus status,
    Instant createdAt,
    Instant updatedAt,
    long version
) {

    static AccountResponse from(
        AccountSnapshot account
    ) {
        return new AccountResponse(
            account.id(),
            account.customerId(),
            account.currency(),
            account.balance().minorUnits(),
            account.status(),
            account.createdAt(),
            account.updatedAt(),
            account.version()
        );
    }
}