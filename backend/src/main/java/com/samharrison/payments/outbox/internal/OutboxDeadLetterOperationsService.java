package com.samharrison.payments.outbox.internal;

import com.samharrison.payments.outbox.OutboxDeadLetterNotFoundException;
import com.samharrison.payments.outbox.OutboxDeadLetterOperations;
import com.samharrison.payments.outbox.OutboxDeadLetterSnapshot;
import com.samharrison.payments.outbox.OutboxReplayCommand;
import com.samharrison.payments.outbox.OutboxReplayConflictException;
import com.samharrison.payments.outbox.OutboxReplayResult;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
class OutboxDeadLetterOperationsService
    implements OutboxDeadLetterOperations {

    private static final int MAX_BATCH_SIZE = 100;

    private final OutboxEventRepository repository;

    private final OutboxReplayAuditRepository
        auditRepository;

    private final Clock clock;

    OutboxDeadLetterOperationsService(
        OutboxEventRepository repository,
        OutboxReplayAuditRepository auditRepository,
        Clock clock
    ) {
        this.repository =
            Objects.requireNonNull(
                repository,
                "repository must not be null"
            );

        this.auditRepository =
            Objects.requireNonNull(
                auditRepository,
                "auditRepository must not be null"
            );

        this.clock =
            Objects.requireNonNull(
                clock,
                "clock must not be null"
            );
    }

    @Override
    @Transactional(readOnly = true)
    public List<OutboxDeadLetterSnapshot>
        findDeadLetters(
            int requestedBatchSize
        ) {
        validateBatchSize(requestedBatchSize);

        return repository
            .findDeadLetters(requestedBatchSize)
            .stream()
            .map(
                OutboxDeadLetterOperationsService
                    ::snapshot
            )
            .toList();
    }

    @Override
    @Transactional
    public OutboxReplayResult replay(
        UUID eventId,
        OutboxReplayCommand command
    ) {
        UUID requiredEventId =
            Objects.requireNonNull(
                eventId,
                "eventId must not be null"
            );

        OutboxReplayCommand requiredCommand =
            Objects.requireNonNull(
                command,
                "command must not be null"
            );

        OutboxEvent event =
            repository
                .findById(requiredEventId)
                .orElseThrow(
                    () ->
                        new OutboxDeadLetterNotFoundException(
                            requiredEventId
                        )
                );

        if (
            event.status()
                != OutboxEventStatus.DEAD_LETTER
        ) {
            throw new OutboxReplayConflictException(
                "Only dead-letter outbox events can be replayed."
            );
        }

        if (
            event.version()
                != requiredCommand.expectedVersion()
        ) {
            throw new OutboxReplayConflictException(
                "The outbox event version changed before replay."
            );
        }

        Instant replayedAt = now();
        long versionBefore = event.version();

        event.replay(replayedAt);
        repository.saveAndFlush(event);

        OutboxReplayAudit audit =
            OutboxReplayAudit.recorded(
                event.id(),
                requiredCommand.actorIdentityUserId(),
                requiredCommand.reason(),
                replayedAt,
                versionBefore
            );

        auditRepository.saveAndFlush(audit);

        return new OutboxReplayResult(
            snapshot(event),
            audit.id(),
            replayedAt
        );
    }

    private static void validateBatchSize(
        int requestedBatchSize
    ) {
        if (
            requestedBatchSize < 1
                || requestedBatchSize > MAX_BATCH_SIZE
        ) {
            throw new IllegalArgumentException(
                "requestedBatchSize must be between 1 and "
                    + MAX_BATCH_SIZE
            );
        }
    }

    private static OutboxDeadLetterSnapshot snapshot(
        OutboxEvent event
    ) {
        return new OutboxDeadLetterSnapshot(
            event.id(),
            event.aggregateType(),
            event.aggregateId(),
            event.eventType(),
            event.schemaVersion(),
            event.payload(),
            event.correlationIdentifier(),
            event.causationIdentifier(),
            event.createdAt(),
            event.updatedAt(),
            event.status().name(),
            event.attemptCount(),
            event.lastErrorCategory(),
            event.lastErrorMessage(),
            event.replayCount(),
            event.lastReplayedAt(),
            event.version()
        );
    }

    private Instant now() {
        return Instant
            .now(clock)
            .truncatedTo(ChronoUnit.MICROS);
    }
}
