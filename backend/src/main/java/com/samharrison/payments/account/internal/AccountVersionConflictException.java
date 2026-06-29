package com.samharrison.payments.account.internal;

import java.io.Serial;
import java.util.UUID;

public final class AccountVersionConflictException
    extends RuntimeException {

    @Serial
    private static final long serialVersionUID = 1L;

    private final UUID accountId;
    private final long expectedVersion;
    private final long actualVersion;

    public AccountVersionConflictException(
        UUID accountId,
        long expectedVersion,
        long actualVersion
    ) {
        super(
            "Account "
                + accountId
                + " has version "
                + actualVersion
                + ", not the expected version "
                + expectedVersion
                + "."
        );

        this.accountId = accountId;
        this.expectedVersion = expectedVersion;
        this.actualVersion = actualVersion;
    }

    public UUID accountId() {
        return accountId;
    }

    public long expectedVersion() {
        return expectedVersion;
    }

    public long actualVersion() {
        return actualVersion;
    }
}