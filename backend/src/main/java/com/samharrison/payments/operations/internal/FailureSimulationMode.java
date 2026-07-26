package com.samharrison.payments.operations.internal;

public enum FailureSimulationMode {
    NONE,
    HTTP_503,
    PAYMENT_503,
    DELAY
}
