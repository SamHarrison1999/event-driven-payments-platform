package com.samharrison.payments.payment.internal;

import static org.assertj.core.api.Assertions.assertThat;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class PaymentMetricsTest {

    private SimpleMeterRegistry registry;
    private PaymentMetrics metrics;

    @BeforeEach
    void setUp() {
        registry = new SimpleMeterRegistry();
        metrics = new PaymentMetrics(registry);
    }

    @Test
    void recordsPaymentLifecycleCounters() {
        metrics.recordSubmission();
        metrics.recordOutcome(completedResponse());
        metrics.recordOutcome(rejectedResponse());
        metrics.recordOutcome(failedResponse());
        metrics.recordIdempotencyReplay();
        metrics.recordConcurrencyRetry();

        assertThat(registry.get("platform.payment.submissions")
            .counter().count()).isEqualTo(1.0);
        assertThat(registry.get("platform.payment.completions")
            .counter().count()).isEqualTo(1.0);
        assertThat(registry.get("platform.payment.rejections")
            .counter().count()).isEqualTo(1.0);
        assertThat(registry.get("platform.payment.failures")
            .counter().count()).isEqualTo(1.0);
        assertThat(registry.get(
                "platform.payment.idempotency.replays"
            ).counter().count())
            .isEqualTo(1.0);
        assertThat(registry.get(
                "platform.payment.concurrency.retries"
            ).counter().count())
            .isEqualTo(1.0);
    }

    @Test
    void recordsPaymentProcessingDuration() {
        var sample = metrics.startProcessing();
        metrics.stopProcessing(sample);

        assertThat(registry.get(
                "platform.payment.processing.duration"
            ).timer().count())
            .isEqualTo(1L);
    }

    private static StoredPaymentResponse completedResponse() {
        return new StoredPaymentResponse(
            201,
            StoredPaymentResponse.APPLICATION_JSON,
            "{\"status\":\"COMPLETED\"}"
        );
    }

    private static StoredPaymentResponse rejectedResponse() {
        return new StoredPaymentResponse(
            422,
            StoredPaymentResponse.APPLICATION_PROBLEM_JSON,
            "{\"status\":422}"
        );
    }

    private static StoredPaymentResponse failedResponse() {
        return new StoredPaymentResponse(
            500,
            StoredPaymentResponse.APPLICATION_PROBLEM_JSON,
            "{\"status\":500}"
        );
    }
}
