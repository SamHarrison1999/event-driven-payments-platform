package com.samharrison.payments.notification.internal;

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
    name = "notification",
    uniqueConstraints = {
        @UniqueConstraint(
            name = "uq_notification_source_event",
            columnNames = "source_event_id"
        )
    },
    indexes = {
        @Index(
            name = "idx_notification_recipient",
            columnList =
                "recipient_identity_user_id,created_at,id"
        ),
        @Index(
            name = "idx_notification_claim",
            columnList =
                "status,next_attempt_at,created_at,id"
        ),
        @Index(
            name = "idx_notification_lease",
            columnList =
                "status,delivery_lease_expires_at,id"
        )
    }
)
class Notification {

    static final int MAX_ATTEMPTS = 5;

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
        name = "recipient_identity_user_id",
        nullable = false,
        updatable = false
    )
    private UUID recipientIdentityUserId;

    @Column(
        name = "payment_id",
        nullable = false,
        updatable = false
    )
    private UUID paymentId;

    @Column(
        name = "amount_minor_units",
        nullable = false,
        updatable = false
    )
    private long amountMinorUnits;

    @Column(
        name = "currency",
        nullable = false,
        updatable = false,
        length = 3
    )
    private String currency;

    @Column(
        name = "payment_completed_at",
        nullable = false,
        updatable = false
    )
    private Instant paymentCompletedAt;

    @Enumerated(EnumType.STRING)
    @Column(
        name = "status",
        nullable = false,
        length = 32
    )
    private NotificationStatus status;

    @Column(
        name = "attempt_count",
        nullable = false
    )
    private int attemptCount;

    @Column(name = "next_attempt_at")
    private Instant nextAttemptAt;

    @Column(name = "delivery_owner_token")
    private UUID deliveryOwnerToken;

    @Column(name = "delivery_lease_expires_at")
    private Instant deliveryLeaseExpiresAt;

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

    @Column(name = "delivered_at")
    private Instant deliveredAt;

    @Version
    @Column(
        name = "version",
        nullable = false
    )
    private long version;

    protected Notification() {
        // Required by JPA.
    }

    private Notification(
        UUID sourceEventId,
        PaymentCompletedNotificationPayload payload,
        Instant createdAt
    ) {
        id = UUID.randomUUID();

        this.sourceEventId =
            Objects.requireNonNull(
                sourceEventId,
                "sourceEventId must not be null"
            );

        PaymentCompletedNotificationPayload
            requiredPayload =
                Objects.requireNonNull(
                    payload,
                    "payload must not be null"
                );

        recipientIdentityUserId =
            requiredPayload.actorIdentityId();
        paymentId = requiredPayload.paymentId();
        amountMinorUnits =
            requiredPayload.amountMinorUnits();
        currency = requiredPayload.currency();
        paymentCompletedAt =
            requiredPayload.completedAt();

        Instant timestamp =
            Objects.requireNonNull(
                createdAt,
                "createdAt must not be null"
            );

        status = NotificationStatus.PENDING;
        attemptCount = 0;
        nextAttemptAt = timestamp;
        this.createdAt = timestamp;
        updatedAt = timestamp;
    }

    static Notification pending(
        UUID sourceEventId,
        PaymentCompletedNotificationPayload payload,
        Instant createdAt
    ) {
        return new Notification(
            sourceEventId,
            payload,
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

        Instant timestamp =
            requireTimestamp(claimedAt);

        boolean pendingAndDue =
            status == NotificationStatus.PENDING
                && nextAttemptAt != null
                && !nextAttemptAt.isAfter(timestamp);

        boolean expiredLease =
            status == NotificationStatus.DELIVERING
                && deliveryLeaseExpiresAt != null
                && !deliveryLeaseExpiresAt
                    .isAfter(timestamp);

        if (!pendingAndDue && !expiredLease) {
            throw new InvalidNotificationStateException(
                "Notification is not claimable."
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

        status = NotificationStatus.DELIVERING;
        deliveryOwnerToken = requiredOwner;
        deliveryLeaseExpiresAt = requiredLease;
        nextAttemptAt = null;
        attemptCount = Math.addExact(attemptCount, 1);
        updatedAt = timestamp;
    }

    void markDelivered(
        UUID ownerToken,
        Instant deliveryTime
    ) {
        requireDeliveryOwner(ownerToken);

        Instant timestamp =
            requireTimestamp(deliveryTime);

        status = NotificationStatus.DELIVERED;
        deliveryOwnerToken = null;
        deliveryLeaseExpiresAt = null;
        nextAttemptAt = null;
        lastErrorCategory = null;
        lastErrorMessage = null;
        deliveredAt = timestamp;
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
        requireDeliveryOwner(ownerToken);

        Instant timestamp =
            requireTimestamp(failedAt);

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

        deliveryOwnerToken = null;
        deliveryLeaseExpiresAt = null;
        deliveredAt = null;
        updatedAt = timestamp;

        if (
            permanent
                || attemptCount >= MAX_ATTEMPTS
        ) {
            status = NotificationStatus.DEAD_LETTER;
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

        status = NotificationStatus.PENDING;
        nextAttemptAt = requiredRetryAt;
    }

    private void requireDeliveryOwner(
        UUID ownerToken
    ) {
        if (
            status != NotificationStatus.DELIVERING
                || !Objects.equals(
                    deliveryOwnerToken,
                    ownerToken
                )
        ) {
            throw new InvalidNotificationStateException(
                "Notification delivery owner does not match."
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
            throw new InvalidNotificationStateException(
                "Notification time moved backwards."
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

    UUID sourceEventId() {
        return sourceEventId;
    }

    UUID recipientIdentityUserId() {
        return recipientIdentityUserId;
    }

    UUID paymentId() {
        return paymentId;
    }

    long amountMinorUnits() {
        return amountMinorUnits;
    }

    String currency() {
        return currency;
    }

    Instant paymentCompletedAt() {
        return paymentCompletedAt;
    }

    NotificationStatus status() {
        return status;
    }

    int attemptCount() {
        return attemptCount;
    }

    Instant nextAttemptAt() {
        return nextAttemptAt;
    }

    UUID deliveryOwnerToken() {
        return deliveryOwnerToken;
    }

    Instant deliveryLeaseExpiresAt() {
        return deliveryLeaseExpiresAt;
    }

    String lastErrorCategory() {
        return lastErrorCategory;
    }

    String lastErrorMessage() {
        return lastErrorMessage;
    }

    Instant createdAt() {
        return createdAt;
    }

    Instant updatedAt() {
        return updatedAt;
    }

    Instant deliveredAt() {
        return deliveredAt;
    }

    long version() {
        return version;
    }
}
