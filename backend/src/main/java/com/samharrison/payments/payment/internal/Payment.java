package com.samharrison.payments.payment.internal;

import com.samharrison.payments.shared.GbpAmount;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;
import jakarta.persistence.UniqueConstraint;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(
    name = "payment",
    uniqueConstraints = {
        @UniqueConstraint(
            name = "uq_payment_ledger_transaction",
            columnNames = "ledger_transaction_id"
        )
    },
    indexes = {
        @Index(
            name = "idx_payment_actor_created",
            columnList = "actor_identity_id,created_at,id"
        ),
        @Index(
            name = "idx_payment_source_account",
            columnList = "source_account_id,created_at,id"
        ),
        @Index(
            name = "idx_payment_destination_account",
            columnList =
                "destination_account_id,created_at,id"
        ),
        @Index(
            name = "idx_payment_status_updated",
            columnList = "status,updated_at,id"
        )
    }
)
public class Payment {

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

    @Column(
        name = "source_account_id",
        nullable = false,
        updatable = false
    )
    private UUID sourceAccountId;

    @Column(
        name = "destination_account_id",
        nullable = false,
        updatable = false
    )
    private UUID destinationAccountId;

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

    @Enumerated(EnumType.STRING)
    @Column(
        name = "status",
        nullable = false,
        length = 32
    )
    private PaymentStatus status;

    @Column(
        name = "ledger_transaction_id"
    )
    private UUID ledgerTransactionId;

    @Enumerated(EnumType.STRING)
    @Column(
        name = "rejection_reason",
        length = 64
    )
    private PaymentRejectionReason rejectionReason;

    @Enumerated(EnumType.STRING)
    @Column(
        name = "failure_reason",
        length = 64
    )
    private PaymentFailureReason failureReason;

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

    @Transient
    private PaymentRequestData requestCache;

    protected Payment() {
        // Required by JPA.
    }

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

        PaymentRequestData requiredRequest =
            Objects.requireNonNull(
                request,
                "request must not be null"
            );

        sourceAccountId =
            requiredRequest.sourceAccountId();

        destinationAccountId =
            requiredRequest.destinationAccountId();

        amountMinorUnits =
            requiredRequest
                .amount()
                .minorUnits();

        currency = GbpAmount.CURRENCY_CODE;
        requestCache = requiredRequest;

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
        if (requestCache == null) {
            requestCache =
                new PaymentRequestData(
                    sourceAccountId,
                    destinationAccountId,
                    GbpAmount.ofMinorUnits(
                        amountMinorUnits
                    )
                );
        }

        return requestCache;
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

    public long version() {
        return version;
    }
}