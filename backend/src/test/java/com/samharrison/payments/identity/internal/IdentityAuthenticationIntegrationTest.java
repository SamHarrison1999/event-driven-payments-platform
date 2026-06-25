package com.samharrison.payments.identity.internal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

@SpringBootTest
@Testcontainers
class IdentityAuthenticationIntegrationTest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer POSTGRESQL =
        new PostgreSQLContainer(
            "postgres:18.4-alpine"
        )
            .withDatabaseName(
                "payments_authentication_test"
            )
            .withUsername("payments_test")
            .withPassword("payments_test_only");

    @Autowired
    private AuthenticationManager authenticationManager;

    @Autowired
    private CustomerRegistrationService registrationService;

    @Autowired
    private IdentityUserRepository repository;

    @BeforeEach
    void clearIdentities() {
        repository.deleteAll();
        repository.flush();
    }

    @Test
    void authenticatesARegisteredCustomer() {
        String rawPassword =
            "correct horse battery staple";

        registrationService.register(
            "Sam.Customer@Example.COM",
            rawPassword
        );

        Authentication authentication =
            authenticationManager.authenticate(
                UsernamePasswordAuthenticationToken
                    .unauthenticated(
                        "  sam.customer@example.com  ",
                        rawPassword
                    )
            );

        assertThat(authentication.isAuthenticated())
            .isTrue();

        assertThat(authentication.getPrincipal())
            .isInstanceOf(
                IdentityUserPrincipal.class
            );

        IdentityUserPrincipal principal =
            (IdentityUserPrincipal)
                authentication.getPrincipal();

        assertThat(principal.getUsername())
            .isEqualTo(
                "sam.customer@example.com"
            );

        assertThat(principal.email())
            .isEqualTo(
                "Sam.Customer@Example.COM"
            );

        assertThat(principal.getAuthorities())
            .extracting(
                GrantedAuthority::getAuthority
            )
            .containsExactly("ROLE_CUSTOMER");

        assertThat(authentication.getCredentials())
            .isNull();

        assertThat(principal.getPassword())
            .isNull();
    }

    @Test
    void rejectsAnIncorrectPassword() {
        registrationService.register(
            "sam.customer@example.com",
            "correct horse battery staple"
        );

        assertThatThrownBy(
            () ->
                authenticationManager.authenticate(
                    UsernamePasswordAuthenticationToken
                        .unauthenticated(
                            "sam.customer@example.com",
                            "incorrect password value"
                        )
                )
        )
            .isInstanceOf(
                BadCredentialsException.class
            );
    }
}
