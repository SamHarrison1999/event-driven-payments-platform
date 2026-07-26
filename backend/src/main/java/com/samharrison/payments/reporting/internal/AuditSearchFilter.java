package com.samharrison.payments.reporting.internal;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import java.util.regex.Pattern;

record AuditSearchFilter(
    Instant from,
    Instant to,
    AuditCategory category,
    String eventType,
    UUID actorIdentityUserId,
    String subjectType,
    String subjectIdentifier,
    String correlationIdentifier,
    AuditSource source,
    String cursor,
    int limit
) {

    private static final Duration MAXIMUM_WINDOW =
        Duration.ofDays(31);

    private static final Pattern SUBJECT_TYPE =
        Pattern.compile(
            "^[a-z][a-z0-9_]{0,63}$"
        );

    private static final Pattern SAFE_IDENTIFIER =
        Pattern.compile(
            "^[A-Za-z0-9][A-Za-z0-9._:-]{0,127}$"
        );

    private static final Pattern CORRELATION_IDENTIFIER =
        Pattern.compile(
            "^[A-Za-z0-9][A-Za-z0-9._-]{0,127}$"
        );

    AuditSearchFilter {
        validateWindow(from, to);
        validateEventType(eventType);
        validateSubject(
            subjectType,
            subjectIdentifier
        );
        validateCorrelation(correlationIdentifier);

        if (cursor != null && cursor.length() > 2048) {
            throw new InvalidAuditQueryException(
                "cursor must not exceed 2048 characters."
            );
        }

        if (
            from == null
                && category == null
                && eventType == null
                && actorIdentityUserId == null
                && subjectType == null
                && correlationIdentifier == null
                && source == null
        ) {
            throw new InvalidAuditQueryException(
                "At least one audit search filter is required."
            );
        }

        if (limit < 1 || limit > 100) {
            throw new InvalidAuditQueryException(
                "limit must be between 1 and 100."
            );
        }
    }

    private static void validateWindow(
        Instant from,
        Instant to
    ) {
        if ((from == null) != (to == null)) {
            throw new InvalidAuditQueryException(
                "from and to must be supplied together."
            );
        }

        if (from == null) {
            return;
        }

        if (!from.isBefore(to)) {
            throw new InvalidAuditQueryException(
                "from must be earlier than to."
            );
        }

        if (
            Duration.between(from, to)
                .compareTo(MAXIMUM_WINDOW) > 0
        ) {
            throw new InvalidAuditQueryException(
                "The audit search window must not exceed "
                    + "31 days."
            );
        }
    }

    private static void validateEventType(
        String eventType
    ) {
        if (
            eventType != null
                && !AuditEventTypeCatalog.contains(
                    eventType
                )
        ) {
            throw new InvalidAuditQueryException(
                "eventType is not supported."
            );
        }
    }

    private static void validateSubject(
        String subjectType,
        String subjectIdentifier
    ) {
        if (
            (subjectType == null)
                != (subjectIdentifier == null)
        ) {
            throw new InvalidAuditQueryException(
                "subjectType and subjectIdentifier "
                    + "must be supplied together."
            );
        }

        if (subjectType == null) {
            return;
        }

        if (
            !SUBJECT_TYPE
                .matcher(subjectType)
                .matches()
            || !SAFE_IDENTIFIER
                .matcher(subjectIdentifier)
                .matches()
        ) {
            throw new InvalidAuditQueryException(
                "The audit subject filter is invalid."
            );
        }
    }

    private static void validateCorrelation(
        String correlationIdentifier
    ) {
        if (
            correlationIdentifier != null
                && !CORRELATION_IDENTIFIER
                    .matcher(correlationIdentifier)
                    .matches()
        ) {
            throw new InvalidAuditQueryException(
                "correlationIdentifier is invalid."
            );
        }
    }
}
