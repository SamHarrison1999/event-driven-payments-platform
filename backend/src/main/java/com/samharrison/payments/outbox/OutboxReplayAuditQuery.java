package com.samharrison.payments.outbox;

import java.time.Instant;
import java.util.Set;
import java.util.UUID;

public record OutboxReplayAuditQuery(
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

    public OutboxReplayAuditQuery {
        eventTypes = Set.copyOf(eventTypes);
    }
}
