package com.samharrison.payments.customer.internal;

import java.io.Serial;
import java.util.UUID;

public final class CustomerOwnershipConflictException
    extends RuntimeException {

    @Serial
    private static final long serialVersionUID = 1L;

    public CustomerOwnershipConflictException(
        UUID identityUserId,
        UUID existingCustomerId,
        UUID requestedCustomerId
    ) {
        super(
            "Identity user "
                + identityUserId
                + " is already assigned to customer "
                + existingCustomerId
                + " and cannot also be assigned to "
                + requestedCustomerId
                + "."
        );
    }
}