package com.samharrison.payments.shared.infrastructure.web;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

class RequestCompletionLoggingFilterTest {

    @Test
    void derivesTheLoggedRouteWithoutQueryParameters() {
        MockHttpServletRequest request = new MockHttpServletRequest(
            "GET",
            "/api/v1/identity/session"
        );
        request.setServletPath("/api/v1/identity/session");
        request.setQueryString("password=must-not-be-logged");

        assertThat(RequestCompletionLoggingFilter.route(request))
            .isEqualTo("/api/v1/identity/session")
            .doesNotContain("password")
            .doesNotContain("must-not-be-logged");
    }
}
