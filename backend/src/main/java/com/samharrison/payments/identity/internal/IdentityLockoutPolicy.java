package com.samharrison.payments.identity.internal;

import java.time.Duration;
import java.util.Objects;

record IdentityLockoutPolicy(
    int maximumFailedAttempts,
    Duration lockDuration
) {

    static final int
        STANDARD_MAXIMUM_FAILED_ATTEMPTS = 5;

    static final Duration
        STANDARD_LOCK_DURATION =
        Duration.ofMinutes(15);

    IdentityLockoutPolicy {
        if (maximumFailedAttempts < 1) {
            throw new IllegalArgumentException(
                "maximumFailedAttempts must be "
                    + "at least 1."
            );
        }

        Objects.requireNonNull(
            lockDuration,
            "lockDuration must not be null"
        );

        if (
            lockDuration.isZero()
                || lockDuration.isNegative()
        ) {
            throw new IllegalArgumentException(
                "lockDuration must be positive."
            );
        }
    }

    static IdentityLockoutPolicy standard() {
        return new IdentityLockoutPolicy(
            STANDARD_MAXIMUM_FAILED_ATTEMPTS,
            STANDARD_LOCK_DURATION
        );
    }
}
