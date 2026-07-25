package com.samharrison.payments.reporting.internal;

final class InvalidAuditQueryException
    extends RuntimeException {

    private static final long serialVersionUID = 1L;

    InvalidAuditQueryException(String message) {
        super(message);
    }
}
