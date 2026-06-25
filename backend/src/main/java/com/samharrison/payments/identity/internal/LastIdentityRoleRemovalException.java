package com.samharrison.payments.identity.internal;

import java.io.Serial;
import java.util.UUID;

final class LastIdentityRoleRemovalException
    extends RuntimeException {

    @Serial
    private static final long serialVersionUID = 1L;

    LastIdentityRoleRemovalException(UUID userId) {
        super(
            "The final role cannot be removed "
                + "from identity user "
                + userId
                + "."
        );
    }
}
