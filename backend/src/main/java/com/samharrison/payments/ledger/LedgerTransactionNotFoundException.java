package com.samharrison.payments.ledger;

import java.io.Serial;
import java.util.UUID;

public final class LedgerTransactionNotFoundException
    extends RuntimeException {

    @Serial
    private static final long serialVersionUID = 1L;

    private final UUID transactionId;

    public LedgerTransactionNotFoundException(
        UUID transactionId
    ) {
        super(
            "Ledger transaction was not found: "
                + transactionId
        );

        this.transactionId = transactionId;
    }

    public UUID transactionId() {
        return transactionId;
    }
}