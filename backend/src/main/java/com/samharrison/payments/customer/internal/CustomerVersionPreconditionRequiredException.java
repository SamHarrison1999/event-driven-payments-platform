package com.samharrison.payments.customer.internal;

import java.io.Serial;

public final class
CustomerVersionPreconditionRequiredException
    extends RuntimeException {

    @Serial
    private static final long serialVersionUID = 1L;

    public CustomerVersionPreconditionRequiredException() {
        super(
            "The If-Match header is required for "
                + "customer updates."
        );
    }
}