package com.samharrison.payments.identity.internal;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import org.junit.jupiter.api.Test;

class IdentitySessionContractTest {

    @Test
    void redactsLoginCredentialsFromLogs() {
        IdentityLoginRequest request =
            new IdentityLoginRequest(
                "sam.customer@example.com",
                "a private password value"
            );

        assertThat(request.toString())
            .isEqualTo(
                "IdentityLoginRequest["
                    + "email=[REDACTED], "
                    + "password=[REDACTED]"
                    + "]"
            )
            .doesNotContain(
                "sam.customer@example.com"
            )
            .doesNotContain(
                "a private password value"
            );
    }

    @Test
    void createsAResponseFromThePrincipal() {
        IdentityUser user =
            IdentityUser.registerCustomer(
                EmailAddress.of(
                    "Sam.Customer@Example.COM"
                ),
                "{test}temporary-password-hash",
                Instant.parse(
                    "2026-06-25T12:00:00Z"
                )
            );

        IdentityUserPrincipal principal =
            IdentityUserPrincipal.from(user);

        IdentitySessionResponse response =
            IdentitySessionResponse.from(
                principal
            );

        assertThat(response.userId())
            .isEqualTo(user.id());

        assertThat(response.email())
            .isEqualTo(
                "Sam.Customer@Example.COM"
            );

        assertThat(response.roles())
            .containsExactly(
                IdentityRole.CUSTOMER
            );
    }
}
