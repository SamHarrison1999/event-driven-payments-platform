package com.samharrison.payments.operations.internal;

final class FailureSimulationDisabledException
    extends RuntimeException {

    private static final long serialVersionUID = 1L;

    FailureSimulationDisabledException() {
        super(
            "Failure simulation is disabled by configuration."
        );
    }
}
