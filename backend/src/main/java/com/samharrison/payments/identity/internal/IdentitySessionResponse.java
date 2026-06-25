package com.samharrison.payments.identity.internal;

import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

public record IdentitySessionResponse(
    UUID userId,
    String email,
    List<IdentityRole> roles
) {

    static IdentitySessionResponse from(
        IdentityUserPrincipal principal
    ) {
        Objects.requireNonNull(
            principal,
            "principal must not be null"
        );

        List<IdentityRole> orderedRoles =
            principal.roles()
                .stream()
                .sorted(
                    Comparator.comparing(
                        IdentityRole::name
                    )
                )
                .toList();

        return new IdentitySessionResponse(
            principal.userId(),
            principal.email(),
            orderedRoles
        );
    }
}
