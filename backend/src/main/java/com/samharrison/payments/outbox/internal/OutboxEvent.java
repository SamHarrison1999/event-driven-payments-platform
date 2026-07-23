package com.samharrison.payments.outbox.internal;

import com.samharrison.payments.outbox.OutboxEventRequest;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(
    name = "outbox_event",
    uniqueConstraints = {
        @UniqueConstraint(
            name = "uq_outbox_event_aggregate_type",
            columnNames = {
                "aggregate_type",
                "aggregate_id",
                "event_type"
            }
        )
    },
    indexes = {
        @Index(
            name = "idx_outbox_event_claim",
            columnList =
                "status,next_attempt_at,created_at,id"
        ),
        @Index(
            name = "idx_outbox_event_lease",
            columnList =
                "status,publication_lease_expires_at,id"
        ),
        @Index(
            name = "idx_outbox_event_aggregate",
            columnList =
                "aggregate_type,aggregate_id,created_at,id"
        )
    }
)
public class OutboxEvent {

    static final int MAX_ATTEMPTS = 5;

    @Id
    @Column(
        name = "id",
        nullable = false,
        updatable = false
    )
    private UUID id;

    @Column(
        name = "aggregate_type",
        nullable = false,
        updatable = false,
        length = 64
    )
    private String aggregateType;

    @Column(
        name = "aggregate_id",
        nullable = false,
        updatable = false
    )
    private UUID aggregateId;

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
        name = "payload",
        nullable = false,
        updatable = false,
        columnDefinition = "TEXT"
    )
    private String payload;

    @Column(
        name = "correlation_id",
        nullable = false,
        updatable = false,
        length = 128
    )
    private String correlationIdentifier;

    @Column(
        name = "causation_id",
        updatable = false,
        length = 128
    )
    private String causationIdentifier;

    @Column(
        name = "created_at",
        nullable = false,
        updatable = false
    )
    private Instant createdAt;

    @Column(
        name = "updated_at",
        nullable = false
    )
    private Instant updatedAt;

    @Enumerated(EnumType.STRING)
    @Column(
        name = "status",
        nullable = false,
        length = 32
    )
    private OutboxEventStatus status;

    @Column(
        name = "attempt_count",
        nullable = false
    )
    private int attemptCount;

    @Column(name = "next_attempt_at")
    private Instant nextAttemptAt;

    @Column(name = "publication_owner_token")
    private UUID publicationOwnerToken;

    @Column(name = "publication_lease_expires_at")
    private Instant publicationLeaseExpiresAt;

    @Column(
        name = "last_error_category",
        length = 64
    )
    private String lastErrorCategory;

    @Column(
        name = "last_error_message",
        length = 512
    )
    private String lastErrorMessage;

    @Column(name = "published_at")
    private Instant publishedAt;

    @Column(
        name = "replay_count",
        nullable = false
    )
    private int replayCount;

    @Column(name = "last_replayed_at")
    private Instant lastReplayedAt;

    @Version
    @Column(
        name = "version",
        nullable = false
    )
    private long version;

    protected OutboxEvent() {
        // Required by JPA.
    }

    private OutboxEvent(
        OutboxEventRequest request,
        String correlationIdentifier,
        Instant createdAt
    ) {
        OutboxEventRequest requiredRequest =
            Objects.requireNonNull(
                request,
                "request must not be null"
            );

        this.id = UUID.randomUUID();
        aggregateType = requiredRequest.aggregateType();
        aggregateId = requiredRequest.aggregateId();
        eventType = requiredRequest.eventType();
        schemaVersion = requiredRequest.schemaVersion();
        payload = requiredRequest.payload();

        this.correlationIdentifier =
            Objects.requireNonNull(
                correlationIdentifier,
                "correlationIdentifier must not be null"
            );

        causationIdentifier =
            requiredRequest.causationIdentifier();

        Instant timestamp =
            Objects.requireNonNull(
                createdAt,
                "createdAt must not be null"
            );

        this.createdAt = timestamp;
        updatedAt = timestamp;
        status = OutboxEventStatus.PENDING;
        attemptCount = 0;
        nextAttemptAt = timestamp;
    }

    static OutboxEvent pending(
        OutboxEventRequest request,
        String correlationIdentifier,
        Instant createdAt
    ) {
        return new OutboxEvent(
            request,
            correlationIdentifier,
            createdAt
        );
    }

    void claim(
        UUID ownerToken,
        Instant leaseExpiresAt,
        Instant claimedAt
    ) {
        UUID requiredOwner =
            Objects.requireNonNull(
                ownerToken,
                "ownerToken must not be null"
            );

        Instant timestamp = requireTimestamp(claimedAt);

        boolean pendingAndDue =
            status == OutboxEventStatus.PENDING
                && nextAttemptAt != null
                && !nextAttemptAt.isAfter(timestamp);

        boolean expiredLease =
            status == OutboxEventStatus.PUBLISHING
                && publicationLeaseExpiresAt != null
                && !publicationLeaseExpiresAt
                    .isAfter(timestamp);

        if (!pendingAndDue && !expiredLease) {
            throw new InvalidOutboxStateException(
                "Outbox event is not claimable."
            );
        }

        Instant requiredLease =
            Objects.requireNonNull(
                leaseExpiresAt,
                "leaseExpiresAt must not be null"
            );

        if (!requiredLease.isAfter(timestamp)) {
            throw new IllegalArgumentException(
                "leaseExpiresAt must be after claimedAt"
            );
        }

        status = OutboxEventStatus.PUBLISHING;
        publicationOwnerToken = requiredOwner;
        publicationLeaseExpiresAt = requiredLease;
        nextAttemptAt = null;
        attemptCount = Math.addExact(attemptCount, 1);
        updatedAt = timestamp;
    }

    void markPublished(
        UUID ownerToken,
        Instant publicationTime
    ) {
        requirePublishingOwner(ownerToken);

        Instant timestamp =
            requireTimestamp(publicationTime);

        status = OutboxEventStatus.PUBLISHED;
        publicationOwnerToken = null;
        publicationLeaseExpiresAt = null;
        nextAttemptAt = null;
        publishedAt = timestamp;
        updatedAt = timestamp;
    }

    void markFailure(
        UUID ownerToken,
        String errorCategory,
        String errorMessage,
        Instant retryAt,
        Instant failedAt,
        boolean permanent
    ) {
        requirePublishingOwner(ownerToken);

        Instant timestamp = requireTimestamp(failedAt);
        lastErrorCategory =
            requireDiagnostic(
                errorCategory,
                "errorCategory",
                64
            );
        lastErrorMessage =
            requireDiagnostic(
                errorMessage,
                "errorMessage",
                512
            );

        publicationOwnerToken = null;
        publicationLeaseExpiresAt = null;
        publishedAt = null;
        updatedAt = timestamp;

        if (permanent || attemptCount >= MAX_ATTEMPTS) {
            status = OutboxEventStatus.DEAD_LETTER;
            nextAttemptAt = null;
            return;
        }

        Instant requiredRetryAt =
            Objects.requireNonNull(
                retryAt,
                "retryAt must not be null"
            );

        if (!requiredRetryAt.isAfter(timestamp)) {
            throw new IllegalArgumentException(
                "retryAt must be after failedAt"
            );
        }

        status = OutboxEventStatus.PENDING;
        nextAttemptAt = requiredRetryAt;
    }

    void replay(
        Instant replayedAt
    ) {
        if (status != OutboxEventStatus.DEAD_LETTER) {
            throw new InvalidOutboxStateException(
                "Only dead-letter events can be replayed."
            );
        }

        Instant timestamp =
            requireTimestamp(replayedAt);

        status = OutboxEventStatus.PENDING;
        attemptCount = 0;
        nextAttemptAt = timestamp;
        publicationOwnerToken = null;
        publicationLeaseExpiresAt = null;
        lastErrorCategory = null;
        lastErrorMessage = null;
        publishedAt = null;
        replayCount = Math.addExact(replayCount, 1);
        lastReplayedAt = timestamp;
        updatedAt = timestamp;
    }

    private void requirePublishingOwner(
        UUID ownerToken
    ) {
        if (
            status != OutboxEventStatus.PUBLISHING
                || !Objects.equals(
                    publicationOwnerToken,
                    ownerToken
                )
        ) {
            throw new InvalidOutboxStateException(
                "Outbox publication owner does not match."
            );
        }
    }

    private Instant requireTimestamp(
        Instant value
    ) {
        Instant timestamp =
            Objects.requireNonNull(
                value,
                "timestamp must not be null"
            );

        if (timestamp.isBefore(updatedAt)) {
            throw new InvalidOutboxStateException(
                "Outbox event time moved backwards."
            );
        }

        return timestamp;
    }

    private static String requireDiagnostic(
        String value,
        String fieldName,
        int maximumLength
    ) {
        String required =
            Objects.requireNonNull(
                value,
                fieldName + " must not be null"
            );

        if (
            required.isBlank()
                || required.length() > maximumLength
        ) {
            throw new IllegalArgumentException(
                fieldName + " is invalid"
            );
        }

        return required;
    }

    UUID id() {
        return id;
    }

    String aggregateType() {
        return aggregateType;
    }

    UUID aggregateId() {
        return aggregateId;
    }

    String eventType() {
        return eventType;
    }

    int schemaVersion() {
        return schemaVersion;
    }

    String payload() {
        return payload;
    }

    String correlationIdentifier() {
        return correlationIdentifier;
    }

    String causationIdentifier() {
        return causationIdentifier;
    }

    Instant createdAt() {
        return createdAt;
    }

    Instant updatedAt() {
        return updatedAt;
    }

    OutboxEventStatus status() {
        return status;
    }

    int attemptCount() {
        return attemptCount;
    }

    Instant nextAttemptAt() {
        return nextAttemptAt;
    }

    UUID publicationOwnerToken() {
        return publicationOwnerToken;
    }

    Instant publicationLeaseExpiresAt() {
        return publicationLeaseExpiresAt;
    }

    String lastErrorCategory() {
        return lastErrorCategory;
    }

    String lastErrorMessage() {
        return lastErrorMessage;
    }

    Instant publishedAt() {
        return publishedAt;
    }

    int replayCount() {
        return replayCount;
    }

    Instant lastReplayedAt() {
        return lastReplayedAt;
    }

    long version() {
        return version;
    }
}
