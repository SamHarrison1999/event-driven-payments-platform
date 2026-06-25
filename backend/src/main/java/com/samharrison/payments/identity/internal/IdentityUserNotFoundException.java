package com.samharrison.payments.identity.internal;

import java.io.Serial;
import java.util.UUID;

final class IdentityUserNotFoundException
    extends RuntimeException {

    @Serial
    private static final long serialVersionUID = 1L;

    IdentityUserNotFoundException(UUID userId) {
        super(
            "Identity user was not found: "
                + userId
        );
    }
}
