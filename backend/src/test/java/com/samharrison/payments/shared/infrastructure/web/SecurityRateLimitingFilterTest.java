package com.samharrison.payments.shared.infrastructure.web;

import static org.assertj.core.api.Assertions.assertThat;

import com.samharrison.payments.shared.config.SecurityRateLimitProperties;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.nio.charset.StandardCharsets;
import jakarta.servlet.ServletException;
import java.io.IOException;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class SecurityRateLimitingFilterTest {

    @Test
    void returnsAProblemResponseAfterTheRouteLimitIsReached()
        throws ServletException, IOException {
        SecurityRateLimitingFilter filter = new SecurityRateLimitingFilter(
            new SecurityRateLimitProperties(
                true,
                Duration.ofMinutes(1),
                10,
                1,
                1,
                1,
                1
            ),
            Clock.fixed(
                Instant.parse("2026-07-26T12:00:00Z"),
                ZoneOffset.UTC
            )
        );

        MockHttpServletRequest first = request();
        MockHttpServletResponse firstResponse =
            new MockHttpServletResponse();
        int[] chainCalls = {0};

        filter.doFilter(
            first,
            firstResponse,
            (servletRequest, servletResponse) -> chainCalls[0]++
        );

        MockHttpServletRequest second = request();
        MockHttpServletResponse secondResponse =
            new MockHttpServletResponse();

        filter.doFilter(
            second,
            secondResponse,
            (servletRequest, servletResponse) -> chainCalls[0]++
        );

        assertThat(chainCalls[0]).isEqualTo(1);
        assertThat(secondResponse.getStatus()).isEqualTo(429);
        assertThat(secondResponse.getHeader("Retry-After"))
            .isEqualTo("60");
        assertThat(secondResponse.getHeader("Cache-Control"))
            .isEqualTo("no-store");
        assertThat(secondResponse.getContentType())
            .isEqualTo("application/problem+json");
        assertThat(secondResponse.getContentAsByteArray())
            .asString(StandardCharsets.UTF_8)
            .contains("RATE_LIMITED")
            .doesNotContain("127.0.0.1");
    }

    @Test
    void ignoresReadRequestsAndUnprotectedRoutes() throws ServletException, IOException {
        SecurityRateLimitingFilter filter = new SecurityRateLimitingFilter(
            new SecurityRateLimitProperties(
                true,
                Duration.ofMinutes(1),
                10,
                1,
                1,
                1,
                1
            ),
            Clock.systemUTC()
        );
        MockHttpServletRequest request =
            new MockHttpServletRequest("GET", "/api/v1/payments");
        MockHttpServletResponse response = new MockHttpServletResponse();
        int[] chainCalls = {0};

        filter.doFilter(
            request,
            response,
            (servletRequest, servletResponse) -> chainCalls[0]++
        );

        assertThat(chainCalls[0]).isEqualTo(1);
        assertThat(response.getStatus()).isEqualTo(200);
    }

    private static MockHttpServletRequest request() {
        MockHttpServletRequest request = new MockHttpServletRequest(
            "POST",
            "/api/v1/identity/session"
        );
        request.setRemoteAddr("127.0.0.1");
        return request;
    }
}
