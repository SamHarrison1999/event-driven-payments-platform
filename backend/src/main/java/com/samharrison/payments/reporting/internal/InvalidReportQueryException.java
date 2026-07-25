package com.samharrison.payments.reporting.internal;

final class InvalidReportQueryException
    extends RuntimeException {

    private static final long serialVersionUID = 1L;

    InvalidReportQueryException(String message) {
        super(message);
    }
}
