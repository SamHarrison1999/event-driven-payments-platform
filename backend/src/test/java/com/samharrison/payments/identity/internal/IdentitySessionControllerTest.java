package com.samharrison.payments.identity.internal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.authentication.logout.LogoutHandler;

@ExtendWith(MockitoExtension.class)
class IdentitySessionControllerTest {

    @Mock
    private IdentityLoginService loginService;

    @Mock
    private LogoutHandler logoutHandler;

    @Mock
    private HttpServletRequest request;

    @Mock
    private HttpServletResponse response;

    @Mock
    private Authentication authentication;

    private IdentitySessionController controller;

    @BeforeEach
    void setUp() {
        controller =
            new IdentitySessionController(
                loginService,
                logoutHandler
            );
    }

    @Test
    void returnsTheAuthenticatedSessionAfterLogin() {
        IdentityLoginRequest loginRequest =
            new IdentityLoginRequest(
                "sam.customer@example.com",
                "a private password value"
            );

        IdentitySessionResponse session =
            new IdentitySessionResponse(
                UUID.fromString(
                    "b3885300-1f20-4ca9-82d1-23575476e0ec"
                ),
                "Sam.Customer@Example.COM",
                List.of(IdentityRole.CUSTOMER)
            );

        when(
            loginService.login(
                loginRequest.email(),
                loginRequest.password(),
                request,
                response
            )
        )
            .thenReturn(session);

        ResponseEntity<IdentitySessionResponse>
            result =
            controller.login(
                loginRequest,
                request,
                response
            );

        assertThat(result.getStatusCode())
            .isEqualTo(HttpStatus.OK);

        assertThat(
            result.getHeaders().getCacheControl()
        )
            .isEqualTo("no-store");

        assertThat(result.getBody())
            .isEqualTo(session);
    }

    @Test
    void returnsUnauthorizedForInvalidCredentials() {
        IdentityLoginRequest loginRequest =
            new IdentityLoginRequest(
                "sam.customer@example.com",
                "an incorrect password value"
            );

        when(
            loginService.login(
                loginRequest.email(),
                loginRequest.password(),
                request,
                response
            )
        )
            .thenThrow(
                new BadCredentialsException(
                    "Bad credentials"
                )
            );

        ResponseEntity<IdentitySessionResponse>
            result =
            controller.login(
                loginRequest,
                request,
                response
            );

        assertThat(result.getStatusCode())
            .isEqualTo(HttpStatus.UNAUTHORIZED);

        assertThat(
            result.getHeaders().getCacheControl()
        )
            .isEqualTo("no-store");

        assertThat(result.getBody())
            .isNull();
    }

    @Test
    void returnsTheCurrentAuthenticatedSession() {
        IdentityUser user =
            IdentityUser.registerCustomer(
                EmailAddress.of(
                    "Sam.Customer@Example.COM"
                ),
                "{test}temporary-password-hash",
                java.time.Instant.parse(
                    "2026-06-25T12:00:00Z"
                )
            );

        IdentityUserPrincipal principal =
            IdentityUserPrincipal.from(user);

        ResponseEntity<IdentitySessionResponse>
            result =
            controller.currentSession(
                principal
            );

        assertThat(result.getStatusCode())
            .isEqualTo(HttpStatus.OK);

        assertThat(
            result.getHeaders().getCacheControl()
        )
            .isEqualTo("no-store");

        assertThat(result.getBody())
            .isNotNull();

        assertThat(result.getBody().userId())
            .isEqualTo(user.id());

        assertThat(result.getBody().email())
            .isEqualTo(
                "Sam.Customer@Example.COM"
            );

        assertThat(result.getBody().roles())
            .containsExactly(
                IdentityRole.CUSTOMER
            );
    }

    @Test
    void logsOutAndReturnsNoContent() {
        ResponseEntity<Void> result =
            controller.logout(
                request,
                response,
                authentication
            );

        verify(logoutHandler)
            .logout(
                request,
                response,
                authentication
            );

        assertThat(result.getStatusCode())
            .isEqualTo(HttpStatus.NO_CONTENT);

        assertThat(
            result.getHeaders().getCacheControl()
        )
            .isEqualTo("no-store");

        assertThat(result.getBody())
            .isNull();
    }
}
