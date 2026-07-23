package com.samharrison.payments.outbox;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public record PublishedOutboxEvent(
    UUID eventId,
    String aggregateType,
    UUID aggregateId,
    String eventType,
    int schemaVersion,
    String payload,
    String correlationIdentifier,
    String causationIdentifier,
    Instant createdAt,
    Instant publishedAt
) {

    public PublishedOutboxEvent {
        eventId =
            Objects.requireNonNull(
                eventId,
                "eventId must not be null"
            );

        aggregateType =
            requireText(
                aggregateType,
                "aggregateType"
            );

        aggregateId =
            Objects.requireNonNull(
                aggregateId,
                "aggregateId must not be null"
            );

        eventType =
            requireText(
                eventType,
                "eventType"
            );

        if (schemaVersion < 1) {
            throw new IllegalArgumentException(
                "schemaVersion must be positive"
            );
        }

        payload = requireText(payload, "payload");

        correlationIdentifier =
            requireText(
                correlationIdentifier,
                "correlationIdentifier"
            );

        createdAt =
            Objects.requireNonNull(
                createdAt,
                "createdAt must not be null"
            );

        publishedAt =
            Objects.requireNonNull(
                publishedAt,
                "publishedAt must not be null"
            );

        if (publishedAt.isBefore(createdAt)) {
            throw new IllegalArgumentException(
                "publishedAt must not precede createdAt"
            );
        }
    }

    public PublishedOutboxCursor cursor() {
        return new PublishedOutboxCursor(
            publishedAt,
            eventId
        );
    }

    private static String requireText(
        String value,
        String fieldName
    ) {
        String required =
            Objects.requireNonNull(
                value,
                fieldName + " must not be null"
            );

        if (required.isBlank()) {
            throw new IllegalArgumentException(
                fieldName + " must not be blank"
            );
        }

        return required;
    }
}
