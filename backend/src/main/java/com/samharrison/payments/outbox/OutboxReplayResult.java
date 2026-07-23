package com.samharrison.payments.outbox;

import java.time.Instant;
import java.util.UUID;

public record OutboxReplayResult(
    OutboxDeadLetterSnapshot event,
    UUID replayAuditId,
    Instant replayedAt
) {
}
