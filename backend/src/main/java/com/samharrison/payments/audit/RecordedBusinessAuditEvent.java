package com.samharrison.payments.audit;

import java.util.Objects;
import java.util.UUID;

public record RecordedBusinessAuditEvent(
    UUID eventId,
    boolean existing
) {

    public RecordedBusinessAuditEvent {
        eventId =
            Objects.requireNonNull(
                eventId,
                "eventId must not be null"
            );
    }
}
