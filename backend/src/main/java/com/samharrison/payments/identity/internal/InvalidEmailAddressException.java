package com.samharrison.payments.identity.internal;

import java.io.Serial;

final class InvalidEmailAddressException
    extends RuntimeException {

    @Serial
    private static final long serialVersionUID = 1L;

    InvalidEmailAddressException(Throwable cause) {
        super(
            "Email address must be valid.",
            cause
        );
    }
}
