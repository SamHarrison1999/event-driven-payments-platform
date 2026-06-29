package com.samharrison.payments.account.internal;

import java.io.Serial;

public final class
AccountVersionPreconditionRequiredException
    extends RuntimeException {

    @Serial
    private static final long serialVersionUID = 1L;

    public AccountVersionPreconditionRequiredException() {
        super(
            "The If-Match header is required for "
                + "account lifecycle updates."
        );
    }
}