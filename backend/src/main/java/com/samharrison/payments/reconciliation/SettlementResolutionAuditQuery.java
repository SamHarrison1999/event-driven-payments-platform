package com.samharrison.payments.reconciliation;

import java.time.Instant;
import java.util.Set;
import java.util.UUID;

public record SettlementResolutionAuditQuery(
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

    public SettlementResolutionAuditQuery {
        eventTypes = Set.copyOf(eventTypes);
    }
}
