package com.samharrison.payments.shared.infrastructure.web;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Objects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 1)
public class RequestCompletionLoggingFilter
    extends OncePerRequestFilter {

    private static final Logger LOGGER =
        LoggerFactory.getLogger(
            RequestCompletionLoggingFilter.class
        );

    private static final String ROUTE_ATTRIBUTE =
        "org.springframework.web.servlet.HandlerMapping.bestMatchingPattern";

    @Override
    protected void doFilterInternal(
        HttpServletRequest request,
        HttpServletResponse response,
        FilterChain filterChain
    ) throws ServletException, IOException {
        long startedAt = System.nanoTime();

        try {
            filterChain.doFilter(request, response);
        } finally {
            long durationMillis =
                (System.nanoTime() - startedAt) / 1_000_000L;

            String route = route(request);
            int status = response.getStatus();

            var event = LOGGER.atLevel(status >= 500
                ? org.slf4j.event.Level.WARN
                : org.slf4j.event.Level.INFO);

            event
                .addKeyValue("event", "http.request.completed")
                .addKeyValue("http.request.method", request.getMethod())
                .addKeyValue("http.route", route)
                .addKeyValue("http.response.status_code", status)
                .addKeyValue("http.server.duration_ms", durationMillis)
                .log("HTTP request completed");
        }
    }

    static String route(HttpServletRequest request) {
        Object value = request.getAttribute(ROUTE_ATTRIBUTE);

        if (value instanceof String route && !route.isBlank()) {
            return route;
        }

        String servletPath = request.getServletPath();

        return Objects.requireNonNullElse(
            servletPath,
            "UNKNOWN"
        );
    }
}
