package com.samharrison.payments.customer.internal;

import java.io.Serial;

public final class
InvalidCustomerVersionPreconditionException
    extends RuntimeException {

    @Serial
    private static final long serialVersionUID = 1L;

    public InvalidCustomerVersionPreconditionException(
        String rawHeader
    ) {
        super(
            "The If-Match header must contain one "
                + "strong customer-version ETag, "
                + "for example \"0\". Received: "
                + String.valueOf(rawHeader)
        );
    }
}