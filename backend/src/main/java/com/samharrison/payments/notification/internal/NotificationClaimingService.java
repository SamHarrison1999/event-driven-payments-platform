package com.samharrison.payments.notification.internal;

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
class NotificationClaimingService {

    private static final int MAX_BATCH_SIZE = 100;

    private static final Duration CLAIM_LEASE =
        Duration.ofSeconds(30);

    private final NotificationRepository repository;
    private final Clock clock;

    NotificationClaimingService(
        NotificationRepository repository,
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
    public List<NotificationDelivery> claim(
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

        List<Notification> notifications =
            repository.findClaimable(
                claimedAt,
                requestedBatchSize
            );

        List<NotificationDelivery> deliveries =
            notifications.stream()
                .map(
                    notification ->
                        claim(
                            notification,
                            claimedAt
                        )
                )
                .toList();

        repository.saveAllAndFlush(notifications);
        return deliveries;
    }

    private static NotificationDelivery claim(
        Notification notification,
        Instant claimedAt
    ) {
        UUID ownerToken = UUID.randomUUID();

        notification.claim(
            ownerToken,
            claimedAt.plus(CLAIM_LEASE),
            claimedAt
        );

        return new NotificationDelivery(
            notification.id(),
            notification.recipientIdentityUserId(),
            notification.paymentId(),
            notification.amountMinorUnits(),
            notification.currency(),
            notification.paymentCompletedAt(),
            ownerToken
        );
    }

    private Instant now() {
        return Instant
            .now(clock)
            .truncatedTo(ChronoUnit.MICROS);
    }
}
