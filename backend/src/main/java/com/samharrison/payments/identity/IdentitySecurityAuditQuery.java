package com.samharrison.payments.identity;

import java.time.Instant;
import java.util.Set;
import java.util.UUID;

public record IdentitySecurityAuditQuery(
    Instant from,
    Instant to,
    Set<String> eventTypes,
    UUID actorIdentityUserId,
    String subjectType,
    String subjectIdentifier,
    String correlationIdentifier,
    Instant cursorOccurredAt,
    String cursorEventId,
    int limit
) {

    public IdentitySecurityAuditQuery {
        eventTypes = Set.copyOf(eventTypes);
    }
}
