package com.samharrison.payments.identity;

import java.io.Serial;
import java.util.UUID;

public final class IdentityUserNotFoundException
    extends RuntimeException {

    @Serial
    private static final long serialVersionUID = 1L;

    public IdentityUserNotFoundException(
        UUID identityUserId
    ) {
        super(
            "Identity user "
                + identityUserId
                + " was not found."
        );
    }
}