package com.samharrison.payments.operations.internal;

public record FailureSimulationState(
    boolean enabled,
    FailureSimulationMode mode,
    long delayMilliseconds,
    String target
) {}
