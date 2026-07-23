package com.samharrison.payments.outbox;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public record PublishedOutboxCursor(
    Instant publishedAt,
    UUID eventId
) {

    private static final UUID FIRST_EVENT_ID =
        new UUID(0L, 0L);

    public PublishedOutboxCursor {
        publishedAt =
            Objects.requireNonNull(
                publishedAt,
                "publishedAt must not be null"
            );

        eventId =
            Objects.requireNonNull(
                eventId,
                "eventId must not be null"
            );
    }

    public static PublishedOutboxCursor beginning() {
        return new PublishedOutboxCursor(
            Instant.EPOCH,
            FIRST_EVENT_ID
        );
    }
}
