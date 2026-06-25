package com.samharrison.payments.identity.internal;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.session.SessionAuthenticationStrategy;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.stereotype.Service;

@Service
public final class IdentityLoginService {

    private final AuthenticationManager authenticationManager;
    private final SessionAuthenticationStrategy
        sessionAuthenticationStrategy;
    private final SecurityContextRepository
        securityContextRepository;
    private final IdentityAuthenticationAttemptService
        authenticationAttemptService;

    public IdentityLoginService(
        AuthenticationManager authenticationManager,
        SessionAuthenticationStrategy
            sessionAuthenticationStrategy,
        SecurityContextRepository
            securityContextRepository,
        IdentityAuthenticationAttemptService
            authenticationAttemptService
    ) {
        this.authenticationManager =
            authenticationManager;
        this.sessionAuthenticationStrategy =
            sessionAuthenticationStrategy;
        this.securityContextRepository =
            securityContextRepository;
        this.authenticationAttemptService =
            authenticationAttemptService;
    }

    IdentitySessionResponse login(
        String rawEmail,
        String rawPassword,
        HttpServletRequest request,
        HttpServletResponse response
    ) {
        authenticationAttemptService
            .prepareForAuthentication(rawEmail);

        Authentication authenticationRequest =
            UsernamePasswordAuthenticationToken
                .unauthenticated(
                    rawEmail,
                    rawPassword
                );

        Authentication authentication;

        try {
            authentication =
                authenticationManager.authenticate(
                    authenticationRequest
                );
        } catch (
            AuthenticationException exception
        ) {
            authenticationAttemptService
                .recordFailure(rawEmail);

            throw exception;
        }

        if (
            !(authentication.getPrincipal()
                instanceof IdentityUserPrincipal principal)
        ) {
            throw new IllegalStateException(
                "Authenticated principal must be "
                    + "an IdentityUserPrincipal."
            );
        }

        authenticationAttemptService.recordSuccess(
            principal.userId()
        );

        sessionAuthenticationStrategy
            .onAuthentication(
                authentication,
                request,
                response
            );

        SecurityContext securityContext =
            SecurityContextHolder
                .createEmptyContext();

        securityContext.setAuthentication(
            authentication
        );

        SecurityContextHolder.setContext(
            securityContext
        );

        securityContextRepository.saveContext(
            securityContext,
            request,
            response
        );

        return IdentitySessionResponse.from(
            principal
        );
    }
}
