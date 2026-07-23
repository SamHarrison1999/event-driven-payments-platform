package com.samharrison.payments.outbox;

import java.time.Instant;
import java.util.UUID;

public record OutboxDeadLetterSnapshot(
    UUID eventId,
    String aggregateType,
    UUID aggregateId,
    String eventType,
    int schemaVersion,
    String payload,
    String correlationIdentifier,
    String causationIdentifier,
    Instant createdAt,
    Instant updatedAt,
    String status,
    int attemptCount,
    String lastErrorCategory,
    String lastErrorMessage,
    int replayCount,
    Instant lastReplayedAt,
    long version
) {
}
