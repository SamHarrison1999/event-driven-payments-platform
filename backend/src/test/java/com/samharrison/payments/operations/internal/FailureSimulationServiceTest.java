package com.samharrison.payments.operations.internal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class FailureSimulationServiceTest {

    private FailureSimulationService service;

    @BeforeEach
    void setUp() {
        service = new FailureSimulationService(
            new FailureSimulationProperties(
                true,
                Duration.ofSeconds(5)
            )
        );
    }

    @Test
    void startsDisabledAndCanBeConfiguredAndCleared() {
        assertThat(service.currentState().mode())
            .isEqualTo(FailureSimulationMode.NONE);

        FailureSimulationState configured = service.configure(
            new FailureSimulationRequest(
                FailureSimulationMode.PAYMENT_503,
                0
            )
        );

        assertThat(configured.mode())
            .isEqualTo(FailureSimulationMode.PAYMENT_503);
        assertThat(configured.target()).isEqualTo("payments");

        assertThat(service.clear().mode())
            .isEqualTo(FailureSimulationMode.NONE);
    }

    @Test
    void rejectsConfigurationWhenFeatureIsDisabled() {
        FailureSimulationService disabled =
            new FailureSimulationService(
                new FailureSimulationProperties(
                    false,
                    Duration.ofSeconds(5)
                )
            );

        assertThatThrownBy(
            () -> disabled.configure(
                new FailureSimulationRequest(
                    FailureSimulationMode.HTTP_503,
                    0
                )
            )
        )
            .isInstanceOf(FailureSimulationDisabledException.class);
    }

    @Test
    void rejectsDelayAboveConfiguredLimit() {
        assertThatThrownBy(
            () -> service.configure(
                new FailureSimulationRequest(
                    FailureSimulationMode.DELAY,
                    5_001
                )
            )
        )
            .isInstanceOf(InvalidFailureSimulationException.class)
            .hasMessageContaining("must not exceed 5000");
    }

    @Test
    void appliesPaymentFailureOnlyToPaymentRoutes() throws Exception {
        service.configure(
            new FailureSimulationRequest(
                FailureSimulationMode.PAYMENT_503,
                0
            )
        );

        MockHttpServletRequest payment = request(
            "/api/v1/payments"
        );
        MockHttpServletResponse paymentResponse =
            new MockHttpServletResponse();

        assertThat(service.apply(payment, paymentResponse))
            .isTrue();
        assertThat(paymentResponse.getStatus())
            .isEqualTo(503);

        MockHttpServletRequest system = request(
            "/api/v1/system/info"
        );
        MockHttpServletResponse systemResponse =
            new MockHttpServletResponse();

        assertThat(service.apply(system, systemResponse))
            .isFalse();
        assertThat(systemResponse.getStatus())
            .isEqualTo(200);
    }

    @Test
    void neverInterceptsItsOwnControlEndpoint() throws Exception {
        service.configure(
            new FailureSimulationRequest(
                FailureSimulationMode.HTTP_503,
                0
            )
        );

        MockHttpServletResponse response =
            new MockHttpServletResponse();

        assertThat(
            service.apply(
                request(
                    "/api/v1/operations/failure-simulation"
                ),
                response
            )
        ).isFalse();
    }

    private static MockHttpServletRequest request(String uri) {
        MockHttpServletRequest request =
            new MockHttpServletRequest();
        request.setRequestURI(uri);
        return request;
    }
}
