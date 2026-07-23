package com.samharrison.payments.notification.internal;

import com.samharrison.payments.outbox.PublishedOutboxEvent;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(
    name = "notification_consumer_failure",
    uniqueConstraints = {
        @UniqueConstraint(
            name =
                "uq_notification_consumer_failure_event",
            columnNames = "source_event_id"
        )
    }
)
class NotificationConsumerFailure {

    @Id
    @Column(
        name = "id",
        nullable = false,
        updatable = false
    )
    private UUID id;

    @Column(
        name = "source_event_id",
        nullable = false,
        updatable = false
    )
    private UUID sourceEventId;

    @Column(
        name = "event_type",
        nullable = false,
        updatable = false,
        length = 128
    )
    private String eventType;

    @Column(
        name = "schema_version",
        nullable = false,
        updatable = false
    )
    private int schemaVersion;

    @Column(
        name = "error_category",
        nullable = false,
        updatable = false,
        length = 64
    )
    private String errorCategory;

    @Column(
        name = "error_message",
        nullable = false,
        updatable = false,
        length = 512
    )
    private String errorMessage;

    @Column(
        name = "occurred_at",
        nullable = false,
        updatable = false
    )
    private Instant occurredAt;

    protected NotificationConsumerFailure() {
        // Required by JPA.
    }

    private NotificationConsumerFailure(
        PublishedOutboxEvent event,
        RuntimeException failure,
        Instant occurredAt
    ) {
        PublishedOutboxEvent requiredEvent =
            Objects.requireNonNull(
                event,
                "event must not be null"
            );

        RuntimeException requiredFailure =
            Objects.requireNonNull(
                failure,
                "failure must not be null"
            );

        id = UUID.randomUUID();
        sourceEventId = requiredEvent.eventId();
        eventType = requiredEvent.eventType();
        schemaVersion = requiredEvent.schemaVersion();
        errorCategory =
            boundedCategory(requiredFailure);
        errorMessage =
            boundedMessage(requiredFailure);

        this.occurredAt =
            Objects.requireNonNull(
                occurredAt,
                "occurredAt must not be null"
            );
    }

    static NotificationConsumerFailure failed(
        PublishedOutboxEvent event,
        RuntimeException failure,
        Instant occurredAt
    ) {
        return new NotificationConsumerFailure(
            event,
            failure,
            occurredAt
        );
    }

    private static String boundedCategory(
        RuntimeException failure
    ) {
        String candidate =
            failure.getClass().getSimpleName();

        if (candidate.isBlank()) {
            candidate = "CONSUMER_FAILURE";
        }

        return candidate.length() <= 64
            ? candidate
            : candidate.substring(0, 64);
    }

    private static String boundedMessage(
        RuntimeException failure
    ) {
        String candidate = failure.getMessage();

        if (candidate == null || candidate.isBlank()) {
            candidate =
                "Notification event consumption failed.";
        }

        return candidate.length() <= 512
            ? candidate
            : candidate.substring(0, 512);
    }
}
