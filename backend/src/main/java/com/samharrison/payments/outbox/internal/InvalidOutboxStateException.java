package com.samharrison.payments.outbox.internal;

final class InvalidOutboxStateException
    extends IllegalStateException {

    private static final long serialVersionUID = 1L;

    InvalidOutboxStateException(
        String message
    ) {
        super(message);
    }
}
