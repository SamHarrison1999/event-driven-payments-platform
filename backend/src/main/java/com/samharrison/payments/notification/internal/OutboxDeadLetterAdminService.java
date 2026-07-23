package com.samharrison.payments.notification.internal;

import com.samharrison.payments.outbox.OutboxDeadLetterOperations;
import com.samharrison.payments.outbox.OutboxDeadLetterSnapshot;
import com.samharrison.payments.outbox.OutboxReplayCommand;
import com.samharrison.payments.outbox.OutboxReplayResult;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;

@Service
@PreAuthorize("hasRole('ADMIN')")
public class OutboxDeadLetterAdminService {

    private final OutboxDeadLetterOperations
        operations;

    public OutboxDeadLetterAdminService(
        OutboxDeadLetterOperations operations
    ) {
        this.operations =
            Objects.requireNonNull(
                operations,
                "operations must not be null"
            );
    }

    public List<OutboxDeadLetterSnapshot>
        findDeadLetters(
            int requestedBatchSize
        ) {
        return operations.findDeadLetters(
            requestedBatchSize
        );
    }

    public OutboxReplayResult replay(
        UUID eventId,
        UUID actorIdentityUserId,
        OutboxReplayRequest request
    ) {
        OutboxReplayRequest requiredRequest =
            Objects.requireNonNull(
                request,
                "request must not be null"
            );

        return operations.replay(
            eventId,
            new OutboxReplayCommand(
                actorIdentityUserId,
                requiredRequest.reason(),
                requiredRequest.expectedVersion()
            )
        );
    }
}
