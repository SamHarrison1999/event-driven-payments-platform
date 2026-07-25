package com.samharrison.payments.outbox;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public record OutboxReplayAuditEvidence(
    UUID auditEventId,
    UUID outboxEventId,
    UUID actorIdentityUserId,
    long eventVersionBefore,
    Instant replayedAt
) {

    public OutboxReplayAuditEvidence {
        auditEventId =
            Objects.requireNonNull(
                auditEventId,
                "auditEventId must not be null"
            );
        outboxEventId =
            Objects.requireNonNull(
                outboxEventId,
                "outboxEventId must not be null"
            );
        actorIdentityUserId =
            Objects.requireNonNull(
                actorIdentityUserId,
                "actorIdentityUserId must not be null"
            );
        replayedAt =
            Objects.requireNonNull(
                replayedAt,
                "replayedAt must not be null"
            );

        if (eventVersionBefore < 0) {
            throw new IllegalArgumentException(
                "eventVersionBefore must not be negative"
            );
        }
    }
}
