package com.samharrison.payments.ledger.internal;

import java.io.Serial;

public final class UnbalancedLedgerTransactionException
    extends InvalidLedgerTransactionException {

    @Serial
    private static final long serialVersionUID = 1L;

    private final long debitMinorUnits;
    private final long creditMinorUnits;

    UnbalancedLedgerTransactionException(
        long debitMinorUnits,
        long creditMinorUnits
    ) {
        super(
            "Ledger transaction is unbalanced: debits="
                + debitMinorUnits
                + " minor units, credits="
                + creditMinorUnits
                + " minor units."
        );

        this.debitMinorUnits = debitMinorUnits;
        this.creditMinorUnits = creditMinorUnits;
    }

    public long debitMinorUnits() {
        return debitMinorUnits;
    }

    public long creditMinorUnits() {
        return creditMinorUnits;
    }
}