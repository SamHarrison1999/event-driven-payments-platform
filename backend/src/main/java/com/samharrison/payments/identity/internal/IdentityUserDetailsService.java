package com.samharrison.payments.identity.internal;

import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

@Service
public final class IdentityUserDetailsService
    implements UserDetailsService {

    private static final String
        INVALID_CREDENTIALS_MESSAGE =
        "Invalid credentials.";

    private final IdentityUserRepository repository;

    public IdentityUserDetailsService(
        IdentityUserRepository repository
    ) {
        this.repository = repository;
    }

    @Override
    public UserDetails loadUserByUsername(
        String rawEmail
    ) {
        EmailAddress emailAddress;

        try {
            emailAddress = EmailAddress.of(rawEmail);
        } catch (IllegalArgumentException ignored) {
            throw new UsernameNotFoundException(
                INVALID_CREDENTIALS_MESSAGE
            );
        }

        return repository
            .findByNormalizedEmail(
                emailAddress.normalizedValue()
            )
            .map(IdentityUserPrincipal::from)
            .orElseThrow(
                () ->
                    new UsernameNotFoundException(
                        INVALID_CREDENTIALS_MESSAGE
                    )
            );
    }
}
