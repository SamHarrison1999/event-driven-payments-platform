package com.samharrison.payments.ledger;

import java.io.Serial;
import java.util.UUID;

public final class LedgerAccountNotFoundException
    extends RuntimeException {

    @Serial
    private static final long serialVersionUID = 1L;

    private final UUID accountId;

    public LedgerAccountNotFoundException(
        UUID accountId
    ) {
        super(
            "Ledger account was not found: "
                + accountId
        );

        this.accountId = accountId;
    }

    public UUID accountId() {
        return accountId;
    }
}