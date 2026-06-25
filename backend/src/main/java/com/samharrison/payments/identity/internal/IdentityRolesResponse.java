package com.samharrison.payments.identity.internal;

import java.util.List;
import java.util.UUID;

public record IdentityRolesResponse(
    UUID userId,
    String email,
    IdentityUserStatus status,
    List<IdentityRole> roles
) {

    static IdentityRolesResponse from(
        IdentityUser user
    ) {
        return new IdentityRolesResponse(
            user.id(),
            user.email(),
            user.status(),
            user.roles()
                .stream()
                .sorted()
                .toList()
        );
    }
}
