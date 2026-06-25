package com.samharrison.payments.identity.internal;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.web.authentication.logout.LogoutHandler;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/identity/session")
public final class IdentitySessionController {

    private final IdentityLoginService loginService;
    private final LogoutHandler logoutHandler;

    public IdentitySessionController(
        IdentityLoginService loginService,
        LogoutHandler logoutHandler
    ) {
        this.loginService = loginService;
        this.logoutHandler = logoutHandler;
    }

    @PostMapping(
        consumes = MediaType.APPLICATION_JSON_VALUE,
        produces = MediaType.APPLICATION_JSON_VALUE
    )
    public ResponseEntity<IdentitySessionResponse>
    login(
        @Valid
        @RequestBody
        IdentityLoginRequest request,
        HttpServletRequest servletRequest,
        HttpServletResponse servletResponse
    ) {
        try {
            IdentitySessionResponse session =
                loginService.login(
                    request.email(),
                    request.password(),
                    servletRequest,
                    servletResponse
                );

            return ResponseEntity
                .ok()
                .cacheControl(CacheControl.noStore())
                .body(session);
        } catch (AuthenticationException ignored) {
            return ResponseEntity
                .status(HttpStatus.UNAUTHORIZED)
                .cacheControl(CacheControl.noStore())
                .build();
        }
    }

    @GetMapping(
        produces = MediaType.APPLICATION_JSON_VALUE
    )
    public ResponseEntity<IdentitySessionResponse>
    currentSession(
        @AuthenticationPrincipal
        IdentityUserPrincipal principal
    ) {
        return ResponseEntity
            .ok()
            .cacheControl(CacheControl.noStore())
            .body(
                IdentitySessionResponse.from(
                    principal
                )
            );
    }

    @DeleteMapping
    public ResponseEntity<Void> logout(
        HttpServletRequest request,
        HttpServletResponse response,
        Authentication authentication
    ) {
        logoutHandler.logout(
            request,
            response,
            authentication
        );

        return ResponseEntity
            .status(HttpStatus.NO_CONTENT)
            .cacheControl(CacheControl.noStore())
            .build();
    }
}
