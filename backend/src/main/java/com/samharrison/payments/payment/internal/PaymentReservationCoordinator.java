package com.samharrison.payments.payment.internal;

import com.samharrison.payments.audit.BusinessAuditEvents;
import com.samharrison.payments.audit.BusinessAuditRecorder;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

@Service
class PaymentReservationCoordinator {

    private static final int MAX_RESERVATION_ATTEMPTS = 2;

    private final PaymentRepository paymentRepository;

    private final PaymentIdempotencyRecordRepository
        idempotencyRepository;

    private final BusinessAuditRecorder auditRecorder;

    private final Clock clock;

    private final TransactionTemplate transactionTemplate;

    PaymentReservationCoordinator(
        PaymentRepository paymentRepository,
        PaymentIdempotencyRecordRepository
            idempotencyRepository,
        BusinessAuditRecorder auditRecorder,
        Clock clock,
        PlatformTransactionManager transactionManager
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

        Objects.requireNonNull(
            transactionManager,
            "transactionManager must not be null"
        );

        transactionTemplate =
            new TransactionTemplate(
                transactionManager
            );

        transactionTemplate.setPropagationBehavior(
            TransactionDefinition
                .PROPAGATION_REQUIRES_NEW
        );
    }

    PaymentReservationResult reserve(
        UUID actorIdentityId,
        IdempotencyKey idempotencyKey,
        PaymentRequestData request
    ) {
        UUID requiredActorIdentityId =
            Objects.requireNonNull(
                actorIdentityId,
                "actorIdentityId must not be null"
            );

        IdempotencyKey requiredIdempotencyKey =
            Objects.requireNonNull(
                idempotencyKey,
                "idempotencyKey must not be null"
            );

        PaymentRequestData requiredRequest =
            Objects.requireNonNull(
                request,
                "request must not be null"
            );

        PaymentRequestFingerprint fingerprint =
            PaymentRequestFingerprint.from(
                requiredRequest
            );

        UUID ownerToken = UUID.randomUUID();
        RuntimeException lastConflict = null;

        for (
            int attempt = 1;
            attempt <= MAX_RESERVATION_ATTEMPTS;
            attempt++
        ) {
            try {
                return inNewTransaction(
                    () ->
                        reserveOrResolve(
                            requiredActorIdentityId,
                            requiredIdempotencyKey,
                            requiredRequest,
                            fingerprint,
                            ownerToken
                        )
                );
            } catch (
                DataIntegrityViolationException
                    | OptimisticLockingFailureException
                        exception
            ) {
                lastConflict = exception;
            }
        }

        throw Objects.requireNonNull(
            lastConflict,
            "reservation conflict must not be null"
        );
    }

    private PaymentReservationResult
    reserveOrResolve(
        UUID actorIdentityId,
        IdempotencyKey idempotencyKey,
        PaymentRequestData request,
        PaymentRequestFingerprint fingerprint,
        UUID ownerToken
    ) {
        Instant evaluatedAt = now();

        Optional<PaymentIdempotencyRecord> existing =
            idempotencyRepository
                .findByActorIdentityIdAndOperationAndIdempotencyKey(
                    actorIdentityId,
                    PaymentOperation
                        .CREATE_INTERNAL_PAYMENT,
                    idempotencyKey.value()
                );

        if (existing.isPresent()) {
            return resolveExisting(
                existing.orElseThrow(),
                fingerprint,
                ownerToken,
                evaluatedAt
            );
        }

        Payment payment =
            Payment.pending(
                actorIdentityId,
                request,
                evaluatedAt
            );

        paymentRepository.saveAndFlush(payment);

        PaymentIdempotencyRecord reservation =
            PaymentIdempotencyRecord.reserve(
                actorIdentityId,
                PaymentOperation
                    .CREATE_INTERNAL_PAYMENT,
                idempotencyKey,
                fingerprint,
                payment.id(),
                ownerToken,
                evaluatedAt
            );

        idempotencyRepository
            .saveAndFlush(reservation);

        auditRecorder.record(
            BusinessAuditEvents.paymentSubmitted(
                evaluatedAt,
                payment.actorIdentityId(),
                payment.id(),
                request.sourceAccountId(),
                request.destinationAccountId(),
                request.amount().minorUnits()
            )
        );

        return new PaymentReservationResult.Acquired(
            payment.id(),
            ownerToken
        );
    }

    private PaymentReservationResult
    resolveExisting(
        PaymentIdempotencyRecord existing,
        PaymentRequestFingerprint fingerprint,
        UUID ownerToken,
        Instant evaluatedAt
    ) {
        if (!existing.matches(fingerprint)) {
            return new PaymentReservationResult.Conflict(
                PaymentReservationResult
                    .Reason
                    .IDEMPOTENCY_KEY_REUSED
            );
        }

        Optional<StoredPaymentResponse> response =
            existing.storedResponse();

        if (response.isPresent()) {
            return new PaymentReservationResult.Replay(
                response.orElseThrow()
            );
        }

        if (!existing.isLeaseExpired(evaluatedAt)) {
            return new PaymentReservationResult.Conflict(
                PaymentReservationResult
                    .Reason
                    .IDEMPOTENCY_REQUEST_IN_PROGRESS
            );
        }

        existing.reclaim(
            ownerToken,
            evaluatedAt
        );

        idempotencyRepository
            .saveAndFlush(existing);

        return new PaymentReservationResult.Acquired(
            existing.paymentId(),
            ownerToken
        );
    }

    private Instant now() {
        return Instant
            .now(clock)
            .truncatedTo(
                ChronoUnit.MICROS
            );
    }

    private <T> T inNewTransaction(
        Supplier<T> work
    ) {
        T result =
            transactionTemplate.execute(
                ignored -> work.get()
            );

        return Objects.requireNonNull(
            result,
            "transaction result must not be null"
        );
    }
}
