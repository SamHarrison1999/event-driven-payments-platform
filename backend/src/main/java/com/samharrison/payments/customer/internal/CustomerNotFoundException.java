package com.samharrison.payments.customer.internal;

import java.io.Serial;
import java.util.UUID;

public final class CustomerNotFoundException
    extends RuntimeException {

    @Serial
    private static final long serialVersionUID = 1L;

    public CustomerNotFoundException(
        UUID customerId
    ) {
        super(
            "Customer was not found: "
                + customerId
        );
    }
}