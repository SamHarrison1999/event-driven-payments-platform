package com.samharrison.payments.operations.internal;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
final class FailureSimulationService {

    private static final Logger LOGGER =
        LoggerFactory.getLogger(FailureSimulationService.class);

    private static final String PAYMENT_PREFIX = "/api/v1/payments";
    private static final String CONTROL_PREFIX =
        "/api/v1/operations/failure-simulation";

    private final FailureSimulationProperties properties;
    private final AtomicReference<Plan> activePlan =
        new AtomicReference<>(Plan.none());

    FailureSimulationService(
        FailureSimulationProperties properties
    ) {
        this.properties = Objects.requireNonNull(
            properties,
            "properties must not be null"
        );
    }

    FailureSimulationState configure(
        FailureSimulationRequest request
    ) {
        Objects.requireNonNull(request, "request must not be null");

        if (!properties.enabled()) {
            throw new FailureSimulationDisabledException();
        }

        FailureSimulationMode mode = Objects.requireNonNull(
            request.mode(),
            "mode must not be null"
        );

        long maxDelay = properties.maxDelay().toMillis();

        if (request.delayMilliseconds() > maxDelay) {
            throw new InvalidFailureSimulationException(
                "delayMilliseconds must not exceed "
                    + maxDelay
                    + "."
            );
        }

        if (mode != FailureSimulationMode.DELAY
            && request.delayMilliseconds() != 0) {
            throw new InvalidFailureSimulationException(
                "delayMilliseconds is only valid for DELAY mode."
            );
        }

        Plan plan = mode == FailureSimulationMode.NONE
            ? Plan.none()
            : new Plan(mode, request.delayMilliseconds());

        activePlan.set(plan);

        LOGGER.warn(
            "Controlled failure simulation configured: mode={}, "
                + "delay_ms={}",
            plan.mode(),
            plan.delayMilliseconds()
        );

        return state(plan);
    }

    FailureSimulationState currentState() {
        return state(activePlan.get());
    }

    FailureSimulationState clear() {
        Plan cleared = Plan.none();
        activePlan.set(cleared);

        LOGGER.warn("Controlled failure simulation cleared");

        return state(cleared);
    }

    boolean apply(
        HttpServletRequest request,
        HttpServletResponse response
    ) throws IOException {
        Objects.requireNonNull(request, "request must not be null");
        Objects.requireNonNull(response, "response must not be null");

        Plan plan = activePlan.get();
        String path = request.getRequestURI();

        if (plan.mode() == FailureSimulationMode.NONE
            || !properties.enabled()
            || path.startsWith(CONTROL_PREFIX)
            || (plan.mode() == FailureSimulationMode.PAYMENT_503
                && !path.startsWith(PAYMENT_PREFIX))) {
            return false;
        }

        if (plan.mode() == FailureSimulationMode.DELAY) {
            delay(plan.delayMilliseconds());
            return false;
        }

        LOGGER.warn(
            "Controlled failure simulation applied: mode={}, "
                + "path={}",
            plan.mode(),
            path
        );

        response.sendError(
            HttpServletResponse.SC_SERVICE_UNAVAILABLE,
            "Controlled failure simulation"
        );

        return true;
    }

    private static void delay(long delayMilliseconds) {
        try {
            Thread.sleep(delayMilliseconds);
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(
                "Failure simulation delay was interrupted.",
                interrupted
            );
        }
    }

    private FailureSimulationState state(Plan plan) {
        String target = switch (plan.mode()) {
            case PAYMENT_503 -> "payments";
            case DELAY, HTTP_503 -> "all-http-requests";
            case NONE -> "none";
        };

        return new FailureSimulationState(
            properties.enabled(),
            plan.mode(),
            plan.delayMilliseconds(),
            target
        );
    }

    private record Plan(
        FailureSimulationMode mode,
        long delayMilliseconds
    ) {
        private Plan {
            Objects.requireNonNull(mode, "mode must not be null");
        }

        private static Plan none() {
            return new Plan(FailureSimulationMode.NONE, 0);
        }
    }
}
