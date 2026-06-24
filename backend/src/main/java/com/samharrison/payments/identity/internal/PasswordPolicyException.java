package com.samharrison.payments.identity.internal;

import java.io.Serial;
import java.util.Objects;

public final class PasswordPolicyException
    extends IllegalArgumentException {

    @Serial
    private static final long serialVersionUID = 1L;

    private final PasswordPolicyViolation violation;

    PasswordPolicyException(
        PasswordPolicyViolation violation,
        String message
    ) {
        super(message);

        this.violation = Objects.requireNonNull(
            violation,
            "violation must not be null"
        );
    }

    public PasswordPolicyViolation violation() {
        return violation;
    }
}
