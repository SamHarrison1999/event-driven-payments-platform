package com.samharrison.payments.identity.internal;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class CustomerRegistrationRequestTest {

    @Test
    void redactsCredentialsFromItsStringForm() {
        String email =
            "sam.customer@example.com";

        String password =
            "this is a secure passphrase";

        CustomerRegistrationRequest request =
            new CustomerRegistrationRequest(
                email,
                password
            );

        assertThat(request.toString())
            .contains(
                "email=[REDACTED]",
                "password=[REDACTED]"
            )
            .doesNotContain(
                email,
                password
            );
    }
}
