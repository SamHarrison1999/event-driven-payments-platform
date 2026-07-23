package com.samharrison.payments.outbox;

import java.util.UUID;

public final class OutboxDeadLetterNotFoundException
    extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public OutboxDeadLetterNotFoundException(
        UUID eventId
    ) {
        super(
            "Outbox dead-letter event was not found: "
                + eventId
        );
    }
}
