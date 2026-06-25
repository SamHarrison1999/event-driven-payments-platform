package com.samharrison.payments.identity.internal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;

@ExtendWith(MockitoExtension.class)
class IdentityUserDetailsServiceTest {

    @Mock
    private IdentityUserRepository repository;

    private IdentityUserDetailsService service;

    @BeforeEach
    void setUp() {
        service =
            new IdentityUserDetailsService(
                repository
            );
    }

    @Test
    void loadsAUserUsingANormalizedEmail() {
        String passwordHash =
            "{test}temporary-password-hash";

        IdentityUser user =
            IdentityUser.registerCustomer(
                EmailAddress.of(
                    "Sam.Customer@Example.COM"
                ),
                passwordHash,
                Instant.parse(
                    "2026-06-25T12:00:00Z"
                )
            );

        when(
            repository.findByNormalizedEmail(
                "sam.customer@example.com"
            )
        )
            .thenReturn(Optional.of(user));

        UserDetails userDetails =
            service.loadUserByUsername(
                "  Sam.Customer@Example.COM  "
            );

        assertThat(userDetails)
            .isInstanceOf(
                IdentityUserPrincipal.class
            );

        assertThat(userDetails.getUsername())
            .isEqualTo(
                "sam.customer@example.com"
            );

        assertThat(userDetails.getPassword())
            .isEqualTo(passwordHash);

        assertThat(userDetails.getAuthorities())
            .extracting(
                GrantedAuthority::getAuthority
            )
            .containsExactly("ROLE_CUSTOMER");

        verify(repository)
            .findByNormalizedEmail(
                "sam.customer@example.com"
            );
    }

    @Test
    void rejectsAnUnknownEmailGenerically() {
        when(
            repository.findByNormalizedEmail(
                "unknown@example.com"
            )
        )
            .thenReturn(Optional.empty());

        assertThatThrownBy(
            () ->
                service.loadUserByUsername(
                    "unknown@example.com"
                )
        )
            .isInstanceOf(
                UsernameNotFoundException.class
            )
            .hasMessage("Invalid credentials.");

        verify(repository)
            .findByNormalizedEmail(
                "unknown@example.com"
            );
    }

    @Test
    void rejectsAMalformedEmailWithoutQuerying()
    {
        assertThatThrownBy(
            () ->
                service.loadUserByUsername(
                    "not-an-email"
                )
        )
            .isInstanceOf(
                UsernameNotFoundException.class
            )
            .hasMessage("Invalid credentials.");

        verifyNoInteractions(repository);
    }
}
