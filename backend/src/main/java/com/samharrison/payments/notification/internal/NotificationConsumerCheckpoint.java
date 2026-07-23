package com.samharrison.payments.notification.internal;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(name = "notification_consumer_checkpoint")
class NotificationConsumerCheckpoint {

    @Id
    @Column(
        name = "consumer_name",
        nullable = false,
        updatable = false,
        length = 64
    )
    private String consumerName;

    @Column(
        name = "last_published_at",
        nullable = false
    )
    private Instant lastPublishedAt;

    @Column(
        name = "last_event_id",
        nullable = false
    )
    private UUID lastEventId;

    @Column(
        name = "updated_at",
        nullable = false
    )
    private Instant updatedAt;

    @Version
    @Column(
        name = "version",
        nullable = false
    )
    private long version;

    protected NotificationConsumerCheckpoint() {
        // Required by JPA.
    }

    void advance(
        Instant publishedAt,
        UUID eventId,
        Instant advancedAt
    ) {
        Instant requiredPublishedAt =
            Objects.requireNonNull(
                publishedAt,
                "publishedAt must not be null"
            );

        UUID requiredEventId =
            Objects.requireNonNull(
                eventId,
                "eventId must not be null"
            );

        boolean movesForward =
            requiredPublishedAt.isAfter(
                lastPublishedAt
            )
                || (
                    requiredPublishedAt.equals(
                        lastPublishedAt
                    )
                        && requiredEventId.compareTo(
                            lastEventId
                        ) > 0
                );

        if (!movesForward) {
            throw new IllegalArgumentException(
                "Consumer checkpoint must move forward"
            );
        }

        lastPublishedAt = requiredPublishedAt;
        lastEventId = requiredEventId;
        updatedAt =
            Objects.requireNonNull(
                advancedAt,
                "advancedAt must not be null"
            );
    }

    String consumerName() {
        return consumerName;
    }

    Instant lastPublishedAt() {
        return lastPublishedAt;
    }

    UUID lastEventId() {
        return lastEventId;
    }

    Instant updatedAt() {
        return updatedAt;
    }
}
