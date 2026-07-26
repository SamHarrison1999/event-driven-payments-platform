package com.samharrison.payments.operations.internal;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public record FailureSimulationRequest(
    @NotNull FailureSimulationMode mode,
    @Min(0)
    @Max(30_000)
    long delayMilliseconds
) {}
