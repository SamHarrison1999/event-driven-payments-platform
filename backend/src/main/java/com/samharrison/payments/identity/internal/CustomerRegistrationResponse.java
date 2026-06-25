package com.samharrison.payments.identity.internal;

import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

public record CustomerRegistrationResponse(
    UUID id,
    String email,
    IdentityUserStatus status,
    List<IdentityRole> roles,
    Instant createdAt
) {

    static CustomerRegistrationResponse from(
        CustomerRegistrationResult result
    ) {
        List<IdentityRole> orderedRoles =
            result.roles()
                .stream()
                .sorted(
                    Comparator.comparing(
                        IdentityRole::name
                    )
                )
                .toList();

        return new CustomerRegistrationResponse(
            result.id(),
            result.email(),
            result.status(),
            orderedRoles,
            result.createdAt()
        );
    }
}
