package com.samharrison.payments.operations.internal;

final class InvalidFailureSimulationException
    extends RuntimeException {

    private static final long serialVersionUID = 1L;

    InvalidFailureSimulationException(String message) {
        super(message);
    }
}
