package com.samharrison.payments.customer;

import java.io.Serial;
import java.util.UUID;

public final class CustomerOwnershipNotFoundException
    extends RuntimeException {

    @Serial
    private static final long serialVersionUID = 1L;

    public CustomerOwnershipNotFoundException(
        UUID identityUserId
    ) {
        super(
            "Identity user "
                + identityUserId
                + " is not assigned to a customer."
        );
    }
}