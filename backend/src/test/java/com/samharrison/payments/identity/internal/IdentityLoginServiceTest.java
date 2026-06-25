package com.samharrison.payments.identity.internal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.same;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.time.Instant;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.session.SessionAuthenticationStrategy;
import org.springframework.security.web.context.SecurityContextRepository;

@ExtendWith(MockitoExtension.class)
class IdentityLoginServiceTest {

    @Mock
    private AuthenticationManager authenticationManager;

    @Mock
    private SessionAuthenticationStrategy
        sessionAuthenticationStrategy;

    @Mock
    private SecurityContextRepository
        securityContextRepository;

    @Mock
    private HttpServletRequest request;

    @Mock
    private HttpServletResponse response;

    private IdentityLoginService service;

    @BeforeEach
    void setUp() {
        service =
            new IdentityLoginService(
                authenticationManager,
                sessionAuthenticationStrategy,
                securityContextRepository
            );
    }

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void authenticatesAndPersistsTheSecurityContext() {
        String rawEmail =
            "sam.customer@example.com";

        String rawPassword =
            "a private password value";

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

        principal.eraseCredentials();

        Authentication authenticated =
            UsernamePasswordAuthenticationToken
                .authenticated(
                    principal,
                    null,
                    principal.getAuthorities()
                );

        when(
            authenticationManager.authenticate(
                org.mockito.ArgumentMatchers.any(
                    Authentication.class
                )
            )
        )
            .thenReturn(authenticated);

        IdentitySessionResponse result =
            service.login(
                rawEmail,
                rawPassword,
                request,
                response
            );

        ArgumentCaptor<Authentication>
            authenticationCaptor =
            ArgumentCaptor.forClass(
                Authentication.class
            );

        verify(authenticationManager)
            .authenticate(
                authenticationCaptor.capture()
            );

        Authentication submittedAuthentication =
            authenticationCaptor.getValue();

        assertThat(
            submittedAuthentication.isAuthenticated()
        )
            .isFalse();

        assertThat(
            submittedAuthentication.getName()
        )
            .isEqualTo(rawEmail);

        assertThat(
            submittedAuthentication.getCredentials()
        )
            .isEqualTo(rawPassword);

        verify(sessionAuthenticationStrategy)
            .onAuthentication(
                same(authenticated),
                same(request),
                same(response)
            );

        ArgumentCaptor<SecurityContext>
            securityContextCaptor =
            ArgumentCaptor.forClass(
                SecurityContext.class
            );

        verify(securityContextRepository)
            .saveContext(
                securityContextCaptor.capture(),
                same(request),
                same(response)
            );

        assertThat(
            securityContextCaptor
                .getValue()
                .getAuthentication()
        )
            .isSameAs(authenticated);

        assertThat(
            SecurityContextHolder
                .getContext()
                .getAuthentication()
        )
            .isSameAs(authenticated);

        assertThat(result.userId())
            .isEqualTo(user.id());

        assertThat(result.email())
            .isEqualTo(
                "Sam.Customer@Example.COM"
            );

        assertThat(result.roles())
            .containsExactly(
                IdentityRole.CUSTOMER
            );
    }
}
