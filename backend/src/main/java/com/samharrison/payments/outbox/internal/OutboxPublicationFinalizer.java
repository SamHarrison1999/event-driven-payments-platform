package com.samharrison.payments.outbox.internal;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Objects;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
class OutboxPublicationFinalizer {

    private static final Duration BASE_RETRY_DELAY =
        Duration.ofSeconds(5);

    private static final Duration MAX_RETRY_DELAY =
        Duration.ofMinutes(5);

    private final OutboxEventRepository repository;
    private final Clock clock;

    OutboxPublicationFinalizer(
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
    public void markPublished(
        UUID eventId,
        UUID ownerToken
    ) {
        OutboxEvent event = requireEvent(eventId);
        event.markPublished(ownerToken, now());
        repository.saveAndFlush(event);
    }

    @Transactional(
        propagation = Propagation.REQUIRES_NEW
    )
    public boolean markFailed(
        UUID eventId,
        UUID ownerToken,
        RuntimeException failure,
        boolean permanent
    ) {
        OutboxEvent event = requireEvent(eventId);
        Instant failedAt = now();

        event.markFailure(
            ownerToken,
            category(failure),
            message(failure),
            retryAt(event, failedAt),
            failedAt,
            permanent
        );

        repository.saveAndFlush(event);

        return event.status()
            == OutboxEventStatus.DEAD_LETTER;
    }

    private OutboxEvent requireEvent(
        UUID eventId
    ) {
        return repository
            .findById(
                Objects.requireNonNull(
                    eventId,
                    "eventId must not be null"
                )
            )
            .orElseThrow(
                () ->
                    new InvalidOutboxStateException(
                        "Outbox event was not found."
                    )
            );
    }

    private static Instant retryAt(
        OutboxEvent event,
        Instant failedAt
    ) {
        long multiplier =
            1L << Math.min(
                event.attemptCount() - 1,
                6
            );

        Duration exponential =
            BASE_RETRY_DELAY.multipliedBy(
                multiplier
            );

        Duration bounded =
            exponential.compareTo(MAX_RETRY_DELAY) > 0
                ? MAX_RETRY_DELAY
                : exponential;

        long jitterSeconds =
            Math.floorMod(
                event.id().hashCode(),
                3
            );

        return failedAt
            .plus(bounded)
            .plusSeconds(jitterSeconds);
    }

    private static String category(
        RuntimeException failure
    ) {
        String simpleName =
            failure.getClass().getSimpleName();

        if (simpleName.isBlank()) {
            return "PUBLICATION_FAILURE";
        }

        return simpleName.length() <= 64
            ? simpleName
            : simpleName.substring(0, 64);
    }

    private static String message(
        RuntimeException failure
    ) {
        String candidate = failure.getMessage();

        if (candidate == null || candidate.isBlank()) {
            candidate = "Outbox publication failed.";
        }

        return candidate.length() <= 512
            ? candidate
            : candidate.substring(0, 512);
    }

    private Instant now() {
        return Instant
            .now(clock)
            .truncatedTo(ChronoUnit.MICROS);
    }
}
