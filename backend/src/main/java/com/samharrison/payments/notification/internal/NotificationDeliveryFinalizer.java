package com.samharrison.payments.notification.internal;

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
class NotificationDeliveryFinalizer {

    private static final Duration BASE_RETRY_DELAY =
        Duration.ofSeconds(5);

    private static final Duration MAX_RETRY_DELAY =
        Duration.ofMinutes(5);

    private final NotificationRepository repository;
    private final Clock clock;

    NotificationDeliveryFinalizer(
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
    public void markDelivered(
        UUID notificationId,
        UUID ownerToken
    ) {
        Notification notification =
            requireNotification(notificationId);

        notification.markDelivered(
            ownerToken,
            now()
        );

        repository.saveAndFlush(notification);
    }

    @Transactional(
        propagation = Propagation.REQUIRES_NEW
    )
    public boolean markFailed(
        UUID notificationId,
        UUID ownerToken,
        RuntimeException failure,
        boolean permanent
    ) {
        Notification notification =
            requireNotification(notificationId);

        Instant failedAt = now();

        notification.markFailure(
            ownerToken,
            category(failure),
            message(failure),
            retryAt(notification, failedAt),
            failedAt,
            permanent
        );

        repository.saveAndFlush(notification);

        return notification.status()
            == NotificationStatus.DEAD_LETTER;
    }

    private Notification requireNotification(
        UUID notificationId
    ) {
        return repository
            .findById(
                Objects.requireNonNull(
                    notificationId,
                    "notificationId must not be null"
                )
            )
            .orElseThrow(
                () ->
                    new InvalidNotificationStateException(
                        "Notification was not found."
                    )
            );
    }

    private static Instant retryAt(
        Notification notification,
        Instant failedAt
    ) {
        long multiplier =
            1L << Math.min(
                notification.attemptCount() - 1,
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
                notification.id().hashCode(),
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
            return "DELIVERY_FAILURE";
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
            candidate =
                "Notification delivery failed.";
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
