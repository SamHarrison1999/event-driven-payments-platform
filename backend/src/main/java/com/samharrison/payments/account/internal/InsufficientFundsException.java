package com.samharrison.payments.account.internal;

import com.samharrison.payments.shared.GbpAmount;
import java.io.Serial;
import java.util.UUID;

public final class InsufficientFundsException
    extends RuntimeException {

    @Serial
    private static final long serialVersionUID = 1L;

    public InsufficientFundsException(
        UUID accountId,
        GbpAmount balance,
        GbpAmount requested
    ) {
        super(
            "Account "
                + accountId
                + " has insufficient funds: balance "
                + balance
                + ", requested "
                + requested
                + "."
        );
    }
}