package com.samharrison.payments.shared.infrastructure.web;

import com.samharrison.payments.shared.config.SecurityRateLimitProperties;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.util.Objects;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 3)
public final class SecurityRateLimitingFilter
    extends OncePerRequestFilter {

    private static final String LOGIN_PATH =
        "/api/v1/identity/session";

    private static final String REGISTRATION_PATH =
        "/api/v1/identity/registrations";

    private static final String PAYMENT_PATH =
        "/api/v1/payments";

    private static final String SETTLEMENT_IMPORT_PATH =
        "/api/v1/settlement-imports";

    private final SecurityRateLimitProperties properties;

    private final FixedWindowRateLimiter limiter;

    private final Clock clock;

    SecurityRateLimitingFilter(
        SecurityRateLimitProperties properties,
        Clock clock
    ) {
        this.properties = Objects.requireNonNull(properties, "properties");
        this.limiter = new FixedWindowRateLimiter(properties.maxTrackedKeys());
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    @Override
    protected void doFilterInternal(
        HttpServletRequest request,
        HttpServletResponse response,
        FilterChain filterChain
    ) throws ServletException, IOException {
        Limit limit = limitFor(request);

        if (!properties.enabled() || limit == null) {
            filterChain.doFilter(request, response);
            return;
        }

        FixedWindowRateLimiter.Decision decision = limiter.tryAcquire(
            key(request, limit.route()),
            limit.maximumRequests(),
            properties.window(),
            Instant.now(clock)
        );

        response.setHeader(
            "X-RateLimit-Limit",
            Integer.toString(limit.maximumRequests())
        );
        response.setHeader(
            "X-RateLimit-Remaining",
            Integer.toString(decision.remaining())
        );

        if (!decision.allowed()) {
            response.setStatus(429);
            response.setHeader(
                "Retry-After",
                Long.toString(decision.retryAfterSeconds())
            );
            response.setHeader("Cache-Control", "no-store");
            response.setContentType(
                MediaType.APPLICATION_PROBLEM_JSON_VALUE
            );
            byte[] body = "{\"type\":\"urn:problem:security:rate-limit\",\"title\":\"Too many requests\",\"status\":429,\"detail\":\"Too many requests. Try again later.\",\"code\":\"RATE_LIMITED\"}"
                .getBytes(StandardCharsets.UTF_8);
            response.setContentLength(body.length);
            response.getOutputStream().write(body);
            return;
        }

        filterChain.doFilter(request, response);
    }

    private static String key(HttpServletRequest request, String route) {
        String remoteAddress = request.getRemoteAddr();
        return route + "|" + (
            remoteAddress == null || remoteAddress.isBlank()
                ? "unknown"
                : remoteAddress
        );
    }

    private Limit limitFor(HttpServletRequest request) {
        if (!"POST".equalsIgnoreCase(request.getMethod())) {
            return null;
        }

        String path = request.getRequestURI();
        String contextPath = request.getContextPath();

        if (contextPath != null && !contextPath.isEmpty()) {
            path = path.substring(contextPath.length());
        }

        if (LOGIN_PATH.equals(path)) {
            return new Limit(LOGIN_PATH, properties.loginRequests());
        }

        if (REGISTRATION_PATH.equals(path)) {
            return new Limit(
                REGISTRATION_PATH,
                properties.registrationRequests()
            );
        }

        if (PAYMENT_PATH.equals(path)) {
            return new Limit(PAYMENT_PATH, properties.paymentRequests());
        }

        if (SETTLEMENT_IMPORT_PATH.equals(path)) {
            return new Limit(
                SETTLEMENT_IMPORT_PATH,
                properties.settlementImportRequests()
            );
        }

        return null;
    }

    private record Limit(String route, int maximumRequests) {
    }
}
