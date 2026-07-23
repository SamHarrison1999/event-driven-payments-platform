package com.samharrison.payments.outbox;

public final class OutboxReplayConflictException
    extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public OutboxReplayConflictException(
        String message
    ) {
        super(message);
    }
}
