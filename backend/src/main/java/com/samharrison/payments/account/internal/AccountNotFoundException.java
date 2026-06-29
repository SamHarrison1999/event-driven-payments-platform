package com.samharrison.payments.account.internal;

import java.io.Serial;
import java.util.UUID;

public final class AccountNotFoundException
    extends RuntimeException {

    @Serial
    private static final long serialVersionUID = 1L;

    public AccountNotFoundException(
        UUID accountId
    ) {
        super(
            "Account "
                + accountId
                + " was not found."
        );
    }
}