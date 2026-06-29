package com.samharrison.payments.identity.internal;

import com.samharrison.payments.identity.CurrentIdentityUser;
import java.util.UUID;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

@Service
class CurrentIdentityUserService
    implements CurrentIdentityUser {

    @Override
    public UUID requireUserId() {
        Authentication authentication =
            SecurityContextHolder
                .getContext()
                .getAuthentication();

        if (
            authentication == null
                || !authentication.isAuthenticated()
        ) {
            throw new AuthenticationCredentialsNotFoundException(
                "An authenticated identity user is required."
            );
        }

        Object principal =
            authentication.getPrincipal();

        if (
            !(principal
                instanceof IdentityUserPrincipal
                    identityPrincipal)
        ) {
            throw new AuthenticationCredentialsNotFoundException(
                "An authenticated identity user is required."
            );
        }

        return identityPrincipal.userId();
    }
}