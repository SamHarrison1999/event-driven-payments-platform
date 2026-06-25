package com.samharrison.payments.identity.internal;

import java.time.Instant;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

record CustomerRegistrationResult(
    UUID id,
    String email,
    IdentityUserStatus status,
    Set<IdentityRole> roles,
    Instant createdAt
) {

    CustomerRegistrationResult {
        Objects.requireNonNull(
            id,
            "id must not be null"
        );

        Objects.requireNonNull(
            email,
            "email must not be null"
        );

        Objects.requireNonNull(
            status,
            "status must not be null"
        );

        roles = Set.copyOf(
            Objects.requireNonNull(
                roles,
                "roles must not be null"
            )
        );

        Objects.requireNonNull(
            createdAt,
            "createdAt must not be null"
        );
    }

    static CustomerRegistrationResult from(
        IdentityUser user
    ) {
        return new CustomerRegistrationResult(
            user.id(),
            user.email(),
            user.status(),
            user.roles(),
            user.createdAt()
        );
    }
}
