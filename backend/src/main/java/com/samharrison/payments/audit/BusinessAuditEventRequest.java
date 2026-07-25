package com.samharrison.payments.audit;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;
import java.util.regex.Pattern;

public record BusinessAuditEventRequest(
    BusinessAuditEventType eventType,
    Instant occurredAt,
    BusinessAuditActor actor,
    String subjectIdentifier,
    String sourceRecordIdentifier,
    String sourceEventIdentifier,
    String correlationIdentifier,
    Map<String, Object> metadata
) {

    private static final int MAX_METADATA_ENTRIES = 16;

    private static final Pattern IDENTIFIER_PATTERN =
        Pattern.compile(
            "[A-Za-z0-9][A-Za-z0-9._:-]{0,127}"
        );

    private static final Pattern CORRELATION_PATTERN =
        Pattern.compile(
            "[A-Za-z0-9][A-Za-z0-9._-]{0,127}"
        );

    private static final Pattern METADATA_KEY_PATTERN =
        Pattern.compile("[a-z][A-Za-z0-9]{0,63}");

    private static final Pattern METADATA_STRING_PATTERN =
        Pattern.compile(
            "[A-Za-z0-9][A-Za-z0-9._:-]{0,127}"
        );

    public BusinessAuditEventRequest {
        eventType =
            Objects.requireNonNull(
                eventType,
                "eventType must not be null"
            );

        occurredAt =
            Objects.requireNonNull(
                occurredAt,
                "occurredAt must not be null"
            )
                .truncatedTo(ChronoUnit.MICROS);

        actor =
            Objects.requireNonNull(
                actor,
                "actor must not be null"
            );

        subjectIdentifier =
            requireIdentifier(
                subjectIdentifier,
                "subjectIdentifier"
            );

        sourceRecordIdentifier =
            requireIdentifier(
                sourceRecordIdentifier,
                "sourceRecordIdentifier"
            );

        sourceEventIdentifier =
            requireIdentifier(
                sourceEventIdentifier,
                "sourceEventIdentifier"
            );

        correlationIdentifier =
            requireCorrelationIdentifier(
                correlationIdentifier
            );

        metadata = immutableMetadata(metadata);
    }

    private static String requireIdentifier(
        String value,
        String fieldName
    ) {
        String required =
            Objects.requireNonNull(
                value,
                fieldName + " must not be null"
            );

        if (
            !IDENTIFIER_PATTERN
                .matcher(required)
                .matches()
        ) {
            throw new InvalidBusinessAuditEventException(
                fieldName + " is invalid."
            );
        }

        return required;
    }

    private static String requireCorrelationIdentifier(
        String value
    ) {
        String required =
            Objects.requireNonNull(
                value,
                "correlationIdentifier must not be null"
            );

        if (
            !CORRELATION_PATTERN
                .matcher(required)
                .matches()
        ) {
            throw new InvalidBusinessAuditEventException(
                "correlationIdentifier is invalid."
            );
        }

        return required;
    }

    private static Map<String, Object>
        immutableMetadata(
            Map<String, Object> metadata
        ) {
        Map<String, Object> required =
            Objects.requireNonNull(
                metadata,
                "metadata must not be null"
            );

        if (
            required.size()
                > MAX_METADATA_ENTRIES
        ) {
            throw new InvalidBusinessAuditEventException(
                "metadata contains too many entries."
            );
        }

        Map<String, Object> ordered =
            new TreeMap<>();

        required.forEach(
            (key, value) ->
                ordered.put(
                    requireMetadataKey(key),
                    requireMetadataValue(value)
                )
        );

        return Collections.unmodifiableMap(
            new LinkedHashMap<>(ordered)
        );
    }

    private static String requireMetadataKey(
        String key
    ) {
        String required =
            Objects.requireNonNull(
                key,
                "metadata key must not be null"
            );

        if (
            !METADATA_KEY_PATTERN
                .matcher(required)
                .matches()
        ) {
            throw new InvalidBusinessAuditEventException(
                "metadata key is invalid."
            );
        }

        return required;
    }

    private static Object requireMetadataValue(
        Object value
    ) {
        Object required =
            Objects.requireNonNull(
                value,
                "metadata value must not be null"
            );

        if (required instanceof String text) {
            if (
                !METADATA_STRING_PATTERN
                    .matcher(text)
                    .matches()
            ) {
                throw new
                    InvalidBusinessAuditEventException(
                        "metadata string value is invalid."
                    );
            }

            return text;
        }

        if (
            required instanceof Byte
                || required instanceof Short
                || required instanceof Integer
                || required instanceof Long
        ) {
            return ((Number) required).longValue();
        }

        if (required instanceof Boolean) {
            return required;
        }

        throw new InvalidBusinessAuditEventException(
            "metadata values must be bounded strings, "
                + "integers or booleans."
        );
    }
}
