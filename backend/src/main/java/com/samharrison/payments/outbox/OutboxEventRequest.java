package com.samharrison.payments.outbox;

import java.util.Objects;
import java.util.UUID;
import java.util.regex.Pattern;

public record OutboxEventRequest(
    String aggregateType,
    UUID aggregateId,
    String eventType,
    int schemaVersion,
    String payload,
    String causationIdentifier
) {

    private static final Pattern TYPE_PATTERN =
        Pattern.compile("[a-z][a-z0-9._-]{0,127}");

    private static final Pattern IDENTIFIER_PATTERN =
        Pattern.compile("[A-Za-z0-9._-]{1,128}");

    private static final int MAX_PAYLOAD_LENGTH = 32_768;

    public OutboxEventRequest {
        aggregateType = requireType(
            aggregateType,
            "aggregateType"
        );

        aggregateId =
            Objects.requireNonNull(
                aggregateId,
                "aggregateId must not be null"
            );

        eventType = requireType(
            eventType,
            "eventType"
        );

        if (schemaVersion < 1) {
            throw new IllegalArgumentException(
                "schemaVersion must be positive"
            );
        }

        payload = requirePayload(payload);

        if (
            causationIdentifier != null
                && !IDENTIFIER_PATTERN
                    .matcher(causationIdentifier)
                    .matches()
        ) {
            throw new IllegalArgumentException(
                "causationIdentifier is invalid"
            );
        }
    }

    private static String requireType(
        String value,
        String fieldName
    ) {
        String required =
            Objects.requireNonNull(
                value,
                fieldName + " must not be null"
            );

        if (!TYPE_PATTERN.matcher(required).matches()) {
            throw new IllegalArgumentException(
                fieldName + " is invalid"
            );
        }

        return required;
    }

    private static String requirePayload(
        String value
    ) {
        String required =
            Objects.requireNonNull(
                value,
                "payload must not be null"
            );

        if (
            required.isBlank()
                || required.length() > MAX_PAYLOAD_LENGTH
        ) {
            throw new IllegalArgumentException(
                "payload must contain between 1 and "
                    + MAX_PAYLOAD_LENGTH
                    + " characters"
            );
        }

        return required;
    }
}
