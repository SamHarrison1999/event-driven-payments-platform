package com.samharrison.payments.payment.internal;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.persistence.Version;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

@Entity
@Table(
    name = "payment_idempotency",
    uniqueConstraints = {
        @UniqueConstraint(
            name = "uq_payment_idempotency_scope",
            columnNames = {
                "actor_identity_id",
                "operation",
                "idempotency_key"
            }
        ),
        @UniqueConstraint(
            name = "uq_payment_idempotency_payment",
            columnNames = "payment_id"
        )
    },
    indexes = {
        @Index(
            name = "idx_payment_idempotency_lease",
            columnList =
                "status,processing_lease_expires_at,id"
        ),
        @Index(
            name = "idx_payment_idempotency_retention",
            columnList =
                "status,retention_expires_at,id"
        )
    }
)
class PaymentIdempotencyRecord {

    static final Duration PROCESSING_LEASE =
        Duration.ofMinutes(5L);

    static final Duration TERMINAL_RETENTION =
        Duration.ofHours(24L);

    @Id
    @Column(
        name = "id",
        nullable = false,
        updatable = false
    )
    private UUID id;

    @Column(
        name = "actor_identity_id",
        nullable = false,
        updatable = false
    )
    private UUID actorIdentityId;

    @Enumerated(EnumType.STRING)
    @Column(
        name = "operation",
        nullable = false,
        updatable = false,
        length = 64
    )
    private PaymentOperation operation;

    @Column(
        name = "idempotency_key",
        nullable = false,
        updatable = false,
        length = IdempotencyKey.MAX_LENGTH
    )
    private String idempotencyKey;

    @Column(
        name = "request_fingerprint",
        nullable = false,
        updatable = false,
        length = PaymentRequestFingerprint.HEX_LENGTH
    )
    private String requestFingerprint;

    @Column(
        name = "payment_id",
        nullable = false,
        updatable = false
    )
    private UUID paymentId;

    @Enumerated(EnumType.STRING)
    @Column(
        name = "status",
        nullable = false,
        length = 32
    )
    private PaymentIdempotencyStatus status;

    @Column(
        name = "processing_owner_token"
    )
    private UUID processingOwnerToken;

    @Column(
        name = "processing_lease_expires_at"
    )
    private Instant processingLeaseExpiresAt;

    @Column(
        name = "response_status"
    )
    private Integer responseStatus;

    @Column(
        name = "response_media_type",
        length = StoredPaymentResponse
            .MAX_MEDIA_TYPE_LENGTH
    )
    private String responseMediaType;

    @Column(
        name = "response_body",
        columnDefinition = "TEXT"
    )
    private String responseBody;

    @Column(
        name = "retention_expires_at"
    )
    private Instant retentionExpiresAt;

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

    @Version
    @Column(
        name = "version",
        nullable = false
    )
    private long version;

    protected PaymentIdempotencyRecord() {
        // Required by JPA.
    }

    private PaymentIdempotencyRecord(
        UUID id,
        UUID actorIdentityId,
        PaymentOperation operation,
        IdempotencyKey idempotencyKey,
        PaymentRequestFingerprint fingerprint,
        UUID paymentId,
        UUID processingOwnerToken,
        Instant reservedAt
    ) {
        this.id =
            Objects.requireNonNull(
                id,
                "id must not be null"
            );

        this.actorIdentityId =
            Objects.requireNonNull(
                actorIdentityId,
                "actorIdentityId must not be null"
            );

        this.operation =
            Objects.requireNonNull(
                operation,
                "operation must not be null"
            );

        this.idempotencyKey =
            Objects.requireNonNull(
                idempotencyKey,
                "idempotencyKey must not be null"
            )
                .value();

        requestFingerprint =
            Objects.requireNonNull(
                fingerprint,
                "fingerprint must not be null"
            )
                .value();

        this.paymentId =
            Objects.requireNonNull(
                paymentId,
                "paymentId must not be null"
            );

        this.processingOwnerToken =
            Objects.requireNonNull(
                processingOwnerToken,
                "processingOwnerToken must not "
                    + "be null"
            );

        Instant timestamp =
            Objects.requireNonNull(
                reservedAt,
                "reservedAt must not be null"
            );

        status =
            PaymentIdempotencyStatus.PROCESSING;

        processingLeaseExpiresAt =
            timestamp.plus(PROCESSING_LEASE);

        createdAt = timestamp;
        updatedAt = timestamp;
    }

    static PaymentIdempotencyRecord reserve(
        UUID actorIdentityId,
        PaymentOperation operation,
        IdempotencyKey idempotencyKey,
        PaymentRequestFingerprint fingerprint,
        UUID paymentId,
        UUID processingOwnerToken,
        Instant reservedAt
    ) {
        return new PaymentIdempotencyRecord(
            UUID.randomUUID(),
            actorIdentityId,
            operation,
            idempotencyKey,
            fingerprint,
            paymentId,
            processingOwnerToken,
            reservedAt
        );
    }

    void reclaim(
        UUID newOwnerToken,
        Instant reclaimedAt
    ) {
        requireProcessing();

        Instant timestamp =
            requireChangeTime(reclaimedAt);

        if (
            timestamp.isBefore(
                processingLeaseExpiresAt
            )
        ) {
            throw new InvalidPaymentException(
                "An active idempotency lease "
                    + "cannot be reclaimed."
            );
        }

        processingOwnerToken =
            Objects.requireNonNull(
                newOwnerToken,
                "newOwnerToken must not be null"
            );

        processingLeaseExpiresAt =
            timestamp.plus(PROCESSING_LEASE);

        updatedAt = timestamp;
    }

    void complete(
        UUID ownerToken,
        StoredPaymentResponse response,
        Instant completedAt
    ) {
        requireProcessing();
        requireOwner(ownerToken);

        StoredPaymentResponse requiredResponse =
            Objects.requireNonNull(
                response,
                "response must not be null"
            );

        Instant timestamp =
            requireChangeTime(completedAt);

        if (
            !timestamp.isBefore(
                processingLeaseExpiresAt
            )
        ) {
            throw new InvalidPaymentException(
                "Idempotency processing lease "
                    + "has expired."
            );
        }

        status =
            PaymentIdempotencyStatus.COMPLETED;

        processingOwnerToken = null;
        processingLeaseExpiresAt = null;

        responseStatus =
            requiredResponse.status();

        responseMediaType =
            requiredResponse.mediaType();

        responseBody =
            requiredResponse.body();

        retentionExpiresAt =
            timestamp.plus(TERMINAL_RETENTION);

        updatedAt = timestamp;
    }

    boolean matches(
        PaymentRequestFingerprint fingerprint
    ) {
        return requestFingerprint.equals(
            Objects.requireNonNull(
                fingerprint,
                "fingerprint must not be null"
            )
                .value()
        );
    }

    boolean isLeaseExpired(
        Instant evaluatedAt
    ) {
        requireProcessing();

        Instant timestamp =
            Objects.requireNonNull(
                evaluatedAt,
                "evaluatedAt must not be null"
            );

        return !timestamp.isBefore(
            processingLeaseExpiresAt
        );
    }

    boolean isOwnedBy(
        UUID ownerToken
    ) {
        return status
                == PaymentIdempotencyStatus.PROCESSING
            && processingOwnerToken.equals(
                Objects.requireNonNull(
                    ownerToken,
                    "ownerToken must not be null"
                )
            );
    }

    Optional<StoredPaymentResponse>
    storedResponse() {
        if (
            status
                != PaymentIdempotencyStatus.COMPLETED
        ) {
            return Optional.empty();
        }

        return Optional.of(
            new StoredPaymentResponse(
                responseStatus,
                responseMediaType,
                responseBody
            )
        );
    }

    private void requireProcessing() {
        if (
            status
                != PaymentIdempotencyStatus.PROCESSING
        ) {
            throw new InvalidPaymentStateTransitionException(
                "Idempotency record "
                    + id
                    + " is not processing."
            );
        }
    }

    private void requireOwner(
        UUID ownerToken
    ) {
        UUID requiredOwnerToken =
            Objects.requireNonNull(
                ownerToken,
                "ownerToken must not be null"
            );

        if (
            !processingOwnerToken.equals(
                requiredOwnerToken
            )
        ) {
            throw new InvalidPaymentException(
                "Idempotency processing owner "
                    + "does not match."
            );
        }
    }

    private Instant requireChangeTime(
        Instant changedAt
    ) {
        Instant timestamp =
            Objects.requireNonNull(
                changedAt,
                "changedAt must not be null"
            );

        if (timestamp.isBefore(updatedAt)) {
            throw new InvalidPaymentException(
                "Idempotency change time must not "
                    + "be before the previous "
                    + "update time."
            );
        }

        return timestamp;
    }

    UUID id() {
        return id;
    }

    UUID actorIdentityId() {
        return actorIdentityId;
    }

    PaymentOperation operation() {
        return operation;
    }

    IdempotencyKey idempotencyKey() {
        return IdempotencyKey.of(
            idempotencyKey
        );
    }

    PaymentRequestFingerprint
    requestFingerprint() {
        return PaymentRequestFingerprint.of(
            requestFingerprint
        );
    }

    UUID paymentId() {
        return paymentId;
    }

    PaymentIdempotencyStatus status() {
        return status;
    }

    UUID processingOwnerToken() {
        return processingOwnerToken;
    }

    Instant processingLeaseExpiresAt() {
        return processingLeaseExpiresAt;
    }

    Instant retentionExpiresAt() {
        return retentionExpiresAt;
    }

    Instant createdAt() {
        return createdAt;
    }

    Instant updatedAt() {
        return updatedAt;
    }

    long version() {
        return version;
    }
}