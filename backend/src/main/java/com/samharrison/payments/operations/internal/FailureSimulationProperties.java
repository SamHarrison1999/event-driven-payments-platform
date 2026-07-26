package com.samharrison.payments.operations.internal;

import java.time.Duration;
import java.util.Objects;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "platform.failure-simulation")
public record FailureSimulationProperties(
    boolean enabled,
    Duration maxDelay
) {

    public FailureSimulationProperties {
        Objects.requireNonNull(
            maxDelay,
            "maxDelay must not be null"
        );

        if (maxDelay.isNegative()) {
            throw new IllegalArgumentException(
                "maxDelay must not be negative"
            );
        }
    }
}
