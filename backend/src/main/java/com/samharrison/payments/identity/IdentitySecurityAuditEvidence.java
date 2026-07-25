package com.samharrison.payments.identity;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public record IdentitySecurityAuditEvidence(
    UUID eventId,
    String eventType,
    UUID actorIdentityUserId,
    UUID subjectIdentityUserId,
    String role,
    Instant occurredAt
) {

    public IdentitySecurityAuditEvidence {
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
        actorIdentityUserId =
            Objects.requireNonNull(
                actorIdentityUserId,
                "actorIdentityUserId must not be null"
            );
        subjectIdentityUserId =
            Objects.requireNonNull(
                subjectIdentityUserId,
                "subjectIdentityUserId must not be null"
            );
        role =
            Objects.requireNonNull(
                role,
                "role must not be null"
            );
        occurredAt =
            Objects.requireNonNull(
                occurredAt,
                "occurredAt must not be null"
            );
    }
}
