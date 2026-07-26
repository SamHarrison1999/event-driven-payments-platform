package com.samharrison.payments.payment.internal;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationRegistry;
import java.util.Objects;
import java.util.function.Supplier;
import org.springframework.stereotype.Component;

@Component
final class PaymentMetrics {

    private final MeterRegistry meterRegistry;
    private final ObservationRegistry observationRegistry;
    private final Counter submissions;
    private final Counter completions;
    private final Counter rejections;
    private final Counter failures;
    private final Counter idempotencyReplays;
    private final Counter concurrencyRetries;
    private final Timer processingDuration;

    PaymentMetrics(
        MeterRegistry meterRegistry,
        ObservationRegistry observationRegistry
    ) {
        this.meterRegistry = Objects.requireNonNull(
            meterRegistry,
            "meterRegistry must not be null"
        );
        this.observationRegistry = Objects.requireNonNull(
            observationRegistry,
            "observationRegistry must not be null"
        );

        submissions = Counter.builder("platform.payment.submissions")
            .description("Payment submission attempts")
            .register(meterRegistry);

        completions = Counter.builder("platform.payment.completions")
            .description("Payments completed successfully")
            .register(meterRegistry);

        rejections = Counter.builder("platform.payment.rejections")
            .description("Payments rejected by business rules")
            .register(meterRegistry);

        failures = Counter.builder("platform.payment.failures")
            .description("Payments finalised as technical failures")
            .register(meterRegistry);

        idempotencyReplays = Counter.builder(
                "platform.payment.idempotency.replays"
            )
            .description("Terminal payment responses replayed")
            .register(meterRegistry);

        concurrencyRetries = Counter.builder(
                "platform.payment.concurrency.retries"
            )
            .description("Payment retries after concurrency conflicts")
            .register(meterRegistry);

        processingDuration = Timer.builder(
                "platform.payment.processing.duration"
            )
            .description("Time spent processing a reserved payment")
            .publishPercentileHistogram()
            .register(meterRegistry);
    }

    void recordSubmission() {
        submissions.increment();
    }

    void recordOutcome(StoredPaymentResponse response) {
        Objects.requireNonNull(response, "response must not be null");

        switch (response.status()) {
            case 201 -> completions.increment();
            case 422 -> rejections.increment();
            case 409, 500 -> failures.increment();
            default -> {
                // Non-terminal or unexpected statuses are not classified.
            }
        }
    }

    void recordIdempotencyReplay() {
        idempotencyReplays.increment();
    }

    void recordConcurrencyRetry() {
        concurrencyRetries.increment();
    }

    Timer.Sample startProcessing() {
        return Timer.start(meterRegistry);
    }

    void stopProcessing(Timer.Sample sample) {
        Objects.requireNonNull(sample, "sample must not be null")
            .stop(processingDuration);
    }

    <T> T observeProcessing(Supplier<T> operation) {
        Objects.requireNonNull(operation, "operation must not be null");

        Observation observation = Observation
            .createNotStarted(
                "platform.payment.processing",
                observationRegistry
            )
            .lowCardinalityKeyValue(
                "payment.operation",
                "processing"
            );

        observation.start();

        Observation.Scope scope = observation.openScope();
        try {
            T result = operation.get();

            observation.lowCardinalityKeyValue(
                "payment.outcome",
                "terminal"
            );

            return result;
        } catch (RuntimeException failure) {
            observation.error(failure);
            observation.lowCardinalityKeyValue(
                "payment.outcome",
                "exception"
            );
            throw failure;
        } finally {
            try {
                scope.close();
            } finally {
                observation.stop();
            }
        }
    }
}
