package com.samharrison.payments.payment.internal;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public final class Payment {

    private final UUID id;
    private final UUID actorIdentityId;
    private final PaymentRequestData request;
    private final Instant createdAt;

    private PaymentStatus status;
    private UUID ledgerTransactionId;
    private PaymentRejectionReason rejectionReason;
    private PaymentFailureReason failureReason;
    private Instant updatedAt;

    private Payment(
        UUID id,
        UUID actorIdentityId,
        PaymentRequestData request,
        Instant createdAt
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

        this.request =
            Objects.requireNonNull(
                request,
                "request must not be null"
            );

        Instant timestamp =
            Objects.requireNonNull(
                createdAt,
                "createdAt must not be null"
            );

        status = PaymentStatus.PENDING;
        this.createdAt = timestamp;
        updatedAt = timestamp;
    }

    public static Payment pending(
        UUID actorIdentityId,
        PaymentRequestData request,
        Instant createdAt
    ) {
        return new Payment(
            UUID.randomUUID(),
            actorIdentityId,
            request,
            createdAt
        );
    }

    public void startProcessing(
        Instant changedAt
    ) {
        transitionFrom(
            PaymentStatus.PENDING,
            PaymentStatus.PROCESSING,
            changedAt
        );
    }

    public void complete(
        UUID postedLedgerTransactionId,
        Instant changedAt
    ) {
        UUID requiredLedgerTransactionId =
            Objects.requireNonNull(
                postedLedgerTransactionId,
                "postedLedgerTransactionId "
                    + "must not be null"
            );

        transitionFrom(
            PaymentStatus.PROCESSING,
            PaymentStatus.COMPLETED,
            changedAt
        );

        ledgerTransactionId =
            requiredLedgerTransactionId;
    }

    public void reject(
        PaymentRejectionReason reason,
        Instant changedAt
    ) {
        PaymentRejectionReason requiredReason =
            Objects.requireNonNull(
                reason,
                "reason must not be null"
            );

        transitionFrom(
            PaymentStatus.PROCESSING,
            PaymentStatus.REJECTED,
            changedAt
        );

        rejectionReason = requiredReason;
    }

    public void fail(
        PaymentFailureReason reason,
        Instant changedAt
    ) {
        PaymentFailureReason requiredReason =
            Objects.requireNonNull(
                reason,
                "reason must not be null"
            );

        transitionFrom(
            PaymentStatus.PROCESSING,
            PaymentStatus.FAILED,
            changedAt
        );

        failureReason = requiredReason;
    }

    private void transitionFrom(
        PaymentStatus requiredCurrentStatus,
        PaymentStatus targetStatus,
        Instant changedAt
    ) {
        if (status != requiredCurrentStatus) {
            throw new InvalidPaymentStateTransitionException(
                "Payment "
                    + id
                    + " cannot transition from "
                    + status
                    + " to "
                    + targetStatus
                    + "."
            );
        }

        Instant timestamp =
            requireChangeTime(changedAt);

        status = targetStatus;
        updatedAt = timestamp;
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
                "Payment change time must not be "
                    + "before the previous update "
                    + "time."
            );
        }

        return timestamp;
    }

    public UUID id() {
        return id;
    }

    public UUID actorIdentityId() {
        return actorIdentityId;
    }

    public PaymentRequestData request() {
        return request;
    }

    public PaymentStatus status() {
        return status;
    }

    public UUID ledgerTransactionId() {
        return ledgerTransactionId;
    }

    public PaymentRejectionReason rejectionReason() {
        return rejectionReason;
    }

    public PaymentFailureReason failureReason() {
        return failureReason;
    }

    public Instant createdAt() {
        return createdAt;
    }

    public Instant updatedAt() {
        return updatedAt;
    }
}
