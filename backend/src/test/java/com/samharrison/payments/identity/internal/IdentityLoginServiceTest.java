package com.samharrison.payments.identity.internal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.same;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.inOrder;

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
import org.springframework.security.authentication.BadCredentialsException;
import org.mockito.InOrder;

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
    private IdentityAuthenticationAttemptService
        authenticationAttemptService;

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
                securityContextRepository,
                authenticationAttemptService
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

        InOrder authenticationOrder =
            inOrder(
                authenticationAttemptService,
                authenticationManager
            );

        authenticationOrder
            .verify(authenticationAttemptService)
            .prepareForAuthentication(rawEmail);

        authenticationOrder
            .verify(authenticationManager)
            .authenticate(
                authenticationCaptor.capture()
            );

        authenticationOrder
            .verify(authenticationAttemptService)
            .recordSuccess(user.id());

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

    @Test
    void recordsFailureAndRethrowsAuthenticationException() {
        String rawEmail =
            "sam.customer@example.com";

        BadCredentialsException exception =
            new BadCredentialsException(
                "Invalid credentials."
            );

        when(
            authenticationManager.authenticate(
                any(Authentication.class)
            )
        )
            .thenThrow(exception);

        assertThatThrownBy(
            () ->
                service.login(
                    rawEmail,
                    "incorrect password",
                    request,
                    response
                )
        )
            .isSameAs(exception);

        InOrder authenticationOrder =
            inOrder(
                authenticationAttemptService,
                authenticationManager
            );

        authenticationOrder
            .verify(authenticationAttemptService)
            .prepareForAuthentication(rawEmail);

        authenticationOrder
            .verify(authenticationManager)
            .authenticate(
                any(Authentication.class)
            );

        authenticationOrder
            .verify(authenticationAttemptService)
            .recordFailure(rawEmail);

        verifyNoInteractions(
            sessionAuthenticationStrategy,
            securityContextRepository
        );

        assertThat(
            SecurityContextHolder
                .getContext()
                .getAuthentication()
        )
            .isNull();
    }

}
