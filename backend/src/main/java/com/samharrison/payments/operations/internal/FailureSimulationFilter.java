package com.samharrison.payments.operations.internal;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Objects;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 2)
final class FailureSimulationFilter extends OncePerRequestFilter {

    private final FailureSimulationService service;

    FailureSimulationFilter(FailureSimulationService service) {
        this.service = Objects.requireNonNull(
            service,
            "service must not be null"
        );
    }

    @Override
    protected void doFilterInternal(
        HttpServletRequest request,
        HttpServletResponse response,
        FilterChain filterChain
    ) throws ServletException, IOException {
        if (service.apply(request, response)) {
            return;
        }

        filterChain.doFilter(request, response);
    }
}
