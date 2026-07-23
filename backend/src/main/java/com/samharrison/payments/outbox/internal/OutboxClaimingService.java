package com.samharrison.payments.outbox.internal;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
class OutboxClaimingService {

    private static final int MAX_BATCH_SIZE = 100;

    private static final Duration CLAIM_LEASE =
        Duration.ofSeconds(30);

    private final OutboxEventRepository repository;
    private final Clock clock;

    OutboxClaimingService(
        OutboxEventRepository repository,
        Clock clock
    ) {
        this.repository =
            Objects.requireNonNull(
                repository,
                "repository must not be null"
            );

        this.clock =
            Objects.requireNonNull(
                clock,
                "clock must not be null"
            );
    }

    @Transactional(
        propagation = Propagation.REQUIRES_NEW
    )
    public List<OutboxPublication> claim(
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

        Instant claimedAt = now();

        List<OutboxEvent> events =
            repository.findClaimable(
                claimedAt,
                requestedBatchSize
            );

        List<OutboxPublication> publications =
            events.stream()
                .map(
                    event ->
                        claim(
                            event,
                            claimedAt
                        )
                )
                .toList();

        repository.saveAllAndFlush(events);
        return publications;
    }

    private static OutboxPublication claim(
        OutboxEvent event,
        Instant claimedAt
    ) {
        UUID ownerToken = UUID.randomUUID();

        event.claim(
            ownerToken,
            claimedAt.plus(CLAIM_LEASE),
            claimedAt
        );

        return new OutboxPublication(
            event.id(),
            event.aggregateType(),
            event.aggregateId(),
            event.eventType(),
            event.schemaVersion(),
            event.payload(),
            event.correlationIdentifier(),
            event.causationIdentifier(),
            ownerToken
        );
    }

    private Instant now() {
        return Instant
            .now(clock)
            .truncatedTo(ChronoUnit.MICROS);
    }
}
