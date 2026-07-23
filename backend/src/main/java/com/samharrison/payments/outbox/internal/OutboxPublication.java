package com.samharrison.payments.outbox.internal;

import java.util.UUID;

record OutboxPublication(
    UUID eventId,
    String aggregateType,
    UUID aggregateId,
    String eventType,
    int schemaVersion,
    String payload,
    String correlationIdentifier,
    String causationIdentifier,
    UUID ownerToken
) {
}
