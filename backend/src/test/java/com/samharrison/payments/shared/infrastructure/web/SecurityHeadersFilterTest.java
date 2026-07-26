package com.samharrison.payments.shared.infrastructure.web;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import jakarta.servlet.ServletException;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class SecurityHeadersFilterTest {

    @Test
    void addsRestrictiveHeadersToApiResponses() throws ServletException, IOException {
        SecurityHeadersFilter filter = new SecurityHeadersFilter();
        MockHttpServletRequest request =
            new MockHttpServletRequest("GET", "/api/v1/system/info");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(
            request,
            response,
            (servletRequest, servletResponse) -> { }
        );

        assertThat(response.getHeader("X-Content-Type-Options"))
            .isEqualTo("nosniff");
        assertThat(response.getHeader("X-Frame-Options"))
            .isEqualTo("DENY");
        assertThat(response.getHeader("Referrer-Policy"))
            .isEqualTo("no-referrer");
        assertThat(response.getHeader("Permissions-Policy"))
            .isEqualTo(SecurityHeadersFilter.PERMISSIONS_POLICY);
        assertThat(response.getHeader("Content-Security-Policy"))
            .isEqualTo(SecurityHeadersFilter.CONTENT_SECURITY_POLICY);
        assertThat(response.getHeader("Strict-Transport-Security"))
            .isNull();
    }

    @Test
    void addsHstsOnlyForHttpsApiResponses() throws ServletException, IOException {
        SecurityHeadersFilter filter = new SecurityHeadersFilter();
        MockHttpServletRequest request =
            new MockHttpServletRequest("GET", "/api/v1/system/info");
        request.setSecure(true);
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(
            request,
            response,
            (servletRequest, servletResponse) -> { }
        );

        assertThat(response.getHeader("Strict-Transport-Security"))
            .isEqualTo("max-age=31536000; includeSubDomains");
    }

    @Test
    void leavesSwaggerAndOtherNonApiSurfacesUntouched() throws ServletException, IOException {
        SecurityHeadersFilter filter = new SecurityHeadersFilter();
        MockHttpServletRequest request =
            new MockHttpServletRequest("GET", "/swagger-ui/index.html");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(
            request,
            response,
            (servletRequest, servletResponse) -> { }
        );

        assertThat(response.getHeader("Content-Security-Policy")).isNull();
    }
}
