package com.samharrison.payments.outbox;

import java.util.Objects;
import java.util.UUID;

public record OutboxReplayCommand(
    UUID actorIdentityUserId,
    String reason,
    long expectedVersion
) {

    private static final int MAX_REASON_LENGTH = 500;

    public OutboxReplayCommand {
        actorIdentityUserId =
            Objects.requireNonNull(
                actorIdentityUserId,
                "actorIdentityUserId must not be null"
            );

        reason =
            Objects.requireNonNull(
                reason,
                "reason must not be null"
            ).strip();

        if (
            reason.isBlank()
                || reason.length() > MAX_REASON_LENGTH
        ) {
            throw new IllegalArgumentException(
                "reason must contain between 1 and "
                    + MAX_REASON_LENGTH
                    + " characters"
            );
        }

        if (expectedVersion < 0) {
            throw new IllegalArgumentException(
                "expectedVersion must not be negative"
            );
        }
    }
}
