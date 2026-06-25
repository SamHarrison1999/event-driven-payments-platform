package com.samharrison.payments.identity.internal;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.GrantedAuthority;

class IdentityUserPrincipalTest {

    @Test
    void mapsIdentityDataAndErasesCredentials() {
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

        IdentityUserPrincipal principal =
            IdentityUserPrincipal.from(user);

        assertThat(principal.userId())
            .isEqualTo(user.id());

        assertThat(principal.email())
            .isEqualTo(
                "Sam.Customer@Example.COM"
            );

        assertThat(principal.roles())
            .containsExactly(
                IdentityRole.CUSTOMER
            );

        assertThat(principal.getUsername())
            .isEqualTo(
                "sam.customer@example.com"
            );

        assertThat(principal.getAuthorities())
            .extracting(
                GrantedAuthority::getAuthority
            )
            .containsExactly("ROLE_CUSTOMER");

        assertThat(principal.getPassword())
            .isEqualTo(passwordHash);

        assertThat(principal.isEnabled())
            .isTrue();

        assertThat(principal.isAccountNonLocked())
            .isTrue();

        assertThat(
            principal.isAccountNonExpired()
        )
            .isTrue();

        assertThat(
            principal.isCredentialsNonExpired()
        )
            .isTrue();

        principal.eraseCredentials();

        assertThat(principal.getPassword())
            .isNull();
    }
}
