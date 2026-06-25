package com.samharrison.payments.identity.internal;

import java.io.Serial;

final class DuplicateEmailException
    extends RuntimeException {

    @Serial
    private static final long serialVersionUID = 1L;

    DuplicateEmailException() {
        super(
            "An account already exists for "
                + "this email address."
        );
    }

    DuplicateEmailException(Throwable cause) {
        super(
            "An account already exists for "
                + "this email address.",
            cause
        );
    }
}
