package com.samharrison.payments.identity.internal;

import tools.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;

@Component
public final class IdentitySecurityProblemHandler
    implements AuthenticationEntryPoint, AccessDeniedHandler {

    private final ObjectMapper objectMapper;

    public IdentitySecurityProblemHandler(
        ObjectMapper objectMapper
    ) {
        this.objectMapper = objectMapper;
    }

    @Override
    public void commence(
        HttpServletRequest request,
        HttpServletResponse response,
        AuthenticationException exception
    ) throws IOException {
        write(
            response,
            HttpStatus.UNAUTHORIZED,
            "Authentication required",
            "Authentication is required to access "
                + "this resource.",
            "urn:problem:security:"
                + "authentication-required",
            "SECURITY_AUTHENTICATION_REQUIRED"
        );
    }

    @Override
    public void handle(
        HttpServletRequest request,
        HttpServletResponse response,
        AccessDeniedException exception
    ) throws IOException {
        write(
            response,
            HttpStatus.FORBIDDEN,
            "Access denied",
            "Access is denied for this resource.",
            "urn:problem:security:access-denied",
            "SECURITY_ACCESS_DENIED"
        );
    }

    private void write(
        HttpServletResponse response,
        HttpStatus status,
        String title,
        String detail,
        String type,
        String code
    ) throws IOException {
        ProblemDetail problem =
            ProblemDetail.forStatusAndDetail(
                status,
                detail
            );

        problem.setTitle(title);
        problem.setType(URI.create(type));
        problem.setProperty("code", code);

        response.setStatus(status.value());
        response.setContentType(
            MediaType.APPLICATION_PROBLEM_JSON_VALUE
        );
        response.setCharacterEncoding(
            StandardCharsets.UTF_8.name()
        );
        response.setHeader(
            HttpHeaders.CACHE_CONTROL,
            "no-store"
        );

        objectMapper.writeValue(
            response.getOutputStream(),
            problem
        );
    }
}