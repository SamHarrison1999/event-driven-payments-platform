package com.samharrison.payments.operations.internal;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.servlet.FilterChain;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class FailureSimulationFilterTest {

    @Test
    void shortCircuitsTheChainForAnActiveHttpFailure() throws Exception {
        FailureSimulationService service =
            new FailureSimulationService(
                new FailureSimulationProperties(
                    true,
                    Duration.ofSeconds(5)
                )
            );
        service.configure(
            new FailureSimulationRequest(
                FailureSimulationMode.HTTP_503,
                0
            )
        );

        FailureSimulationFilter filter =
            new FailureSimulationFilter(service);
        MockHttpServletRequest request =
            new MockHttpServletRequest("GET", "/api/v1/system/info");
        MockHttpServletResponse response =
            new MockHttpServletResponse();
        FilterChain chain = new MockFilterChain();

        filter.doFilter(request, response, chain);

        assertThat(response.getStatus()).isEqualTo(503);
        assertThat(((MockFilterChain) chain).getRequest())
            .isNull();
    }
}
