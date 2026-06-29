package com.samharrison.payments.customer.internal;

import java.io.Serial;

public final class InvalidCustomerNameException
    extends IllegalArgumentException {

    @Serial
    private static final long serialVersionUID = 1L;

    public InvalidCustomerNameException(
        String message
    ) {
        super(message);
    }
}