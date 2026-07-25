package com.samharrison.payments.reporting.internal;

import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

public record AuditEventResponse(
    String eventId,
    AuditSource source,
    AuditCategory category,
    String eventType,
    int schemaVersion,
    Instant occurredAt,
    String actorKind,
    UUID actorIdentityUserId,
    String subjectType,
    String subjectIdentifier,
    String correlationIdentifier,
    Map<String, Object> details
) {

    public AuditEventResponse {
        eventId =
            Objects.requireNonNull(
                eventId,
                "eventId must not be null"
            );
        source =
            Objects.requireNonNull(
                source,
                "source must not be null"
            );
        category =
            Objects.requireNonNull(
                category,
                "category must not be null"
            );
        eventType =
            Objects.requireNonNull(
                eventType,
                "eventType must not be null"
            );
        occurredAt =
            Objects.requireNonNull(
                occurredAt,
                "occurredAt must not be null"
            );
        actorKind =
            Objects.requireNonNull(
                actorKind,
                "actorKind must not be null"
            );
        subjectType =
            Objects.requireNonNull(
                subjectType,
                "subjectType must not be null"
            );
        subjectIdentifier =
            Objects.requireNonNull(
                subjectIdentifier,
                "subjectIdentifier must not be null"
            );
        details = Map.copyOf(details);
    }
}
