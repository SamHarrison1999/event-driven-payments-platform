package com.samharrison.payments.outbox.internal;

final class PermanentOutboxPublicationException
    extends RuntimeException {

    private static final long serialVersionUID = 1L;

    PermanentOutboxPublicationException(
        String message
    ) {
        super(message);
    }
}
