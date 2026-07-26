package com.samharrison.payments.audit;

import java.time.Instant;
import java.util.Set;
import java.util.UUID;

public record BusinessAuditReadCriteria(
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

    public BusinessAuditReadCriteria {
        eventTypes = Set.copyOf(eventTypes);
    }
}
