package com.samharrison.payments.payment.internal;

import com.samharrison.payments.audit.BusinessAuditEvents;
import com.samharrison.payments.audit.BusinessAuditRecorder;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Objects;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
class PaymentFailureFinalizer {

    private final PaymentRepository paymentRepository;

    private final PaymentIdempotencyRecordRepository
        idempotencyRepository;

    private final BusinessAuditRecorder auditRecorder;

    private final Clock clock;

    PaymentFailureFinalizer(
        PaymentRepository paymentRepository,
        PaymentIdempotencyRecordRepository
            idempotencyRepository,
        BusinessAuditRecorder auditRecorder,
        Clock clock
    ) {
        this.paymentRepository =
            Objects.requireNonNull(
                paymentRepository,
                "paymentRepository must not be null"
            );

        this.idempotencyRepository =
            Objects.requireNonNull(
                idempotencyRepository,
                "idempotencyRepository must not be null"
            );

        this.auditRecorder =
            Objects.requireNonNull(
                auditRecorder,
                "auditRecorder must not be null"
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
    public StoredPaymentResponse finalizeFailure(
        UUID paymentId,
        UUID ownerToken,
        PaymentFailureReason reason
    ) {
        UUID requiredPaymentId =
            Objects.requireNonNull(
                paymentId,
                "paymentId must not be null"
            );

        UUID requiredOwnerToken =
            Objects.requireNonNull(
                ownerToken,
                "ownerToken must not be null"
            );

        PaymentFailureReason requiredReason =
            Objects.requireNonNull(
                reason,
                "reason must not be null"
            );

        Instant failedAt = now();

        PaymentIdempotencyRecord reservation =
            idempotencyRepository
                .findByPaymentId(requiredPaymentId)
                .orElseThrow(
                    () ->
                        new InvalidPaymentException(
                            "Payment reservation was not found."
                        )
                );

        if (!reservation.isOwnedBy(requiredOwnerToken)) {
            throw new InvalidPaymentException(
                "Payment reservation owner does not match."
            );
        }

        if (reservation.isLeaseExpired(failedAt)) {
            throw new InvalidPaymentException(
                "Payment reservation lease has expired."
            );
        }

        Payment payment =
            paymentRepository
                .findById(requiredPaymentId)
                .orElseThrow(
                    () ->
                        new InvalidPaymentException(
                            "Reserved payment was not found."
                        )
                );

        if (payment.ledgerTransactionId() != null) {
            throw new InvalidPaymentException(
                "A failed payment must not reference "
                    + "a ledger transaction."
            );
        }

        payment.startProcessing(failedAt);

        payment.fail(
            requiredReason,
            failedAt
        );

        StoredPaymentResponse response =
            PaymentResponseFactory.failed(
                payment.id(),
                requiredReason
            );

        reservation.complete(
            requiredOwnerToken,
            response,
            failedAt
        );

        paymentRepository.saveAndFlush(payment);

        idempotencyRepository
            .saveAndFlush(reservation);

        auditRecorder.record(
            BusinessAuditEvents.paymentFailed(
                failedAt,
                payment.id(),
                requiredReason.code()
            )
        );

        return response;
    }

    private Instant now() {
        return Instant
            .now(clock)
            .truncatedTo(
                ChronoUnit.MICROS
            );
    }
}
