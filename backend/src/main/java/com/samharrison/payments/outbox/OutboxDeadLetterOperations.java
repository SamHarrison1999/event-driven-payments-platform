package com.samharrison.payments.outbox;

import java.util.List;
import java.util.UUID;

public interface OutboxDeadLetterOperations {

    List<OutboxDeadLetterSnapshot> findDeadLetters(
        int requestedBatchSize
    );

    OutboxReplayResult replay(
        UUID eventId,
        OutboxReplayCommand command
    );
}
