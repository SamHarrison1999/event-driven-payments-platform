package com.samharrison.payments.audit;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public record BusinessAuditEvidence(
    UUID eventId,
    String eventType,
    int schemaVersion,
    Instant occurredAt,
    BusinessAuditActorKind actorKind,
    UUID actorIdentityUserId,
    String subjectType,
    String subjectIdentifier,
    String correlationIdentifier,
    String metadata
) {

    public BusinessAuditEvidence {
        eventId =
            Objects.requireNonNull(
                eventId,
                "eventId must not be null"
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
        correlationIdentifier =
            Objects.requireNonNull(
                correlationIdentifier,
                "correlationIdentifier must not be null"
            );
        metadata =
            Objects.requireNonNull(
                metadata,
                "metadata must not be null"
            );
    }
}
