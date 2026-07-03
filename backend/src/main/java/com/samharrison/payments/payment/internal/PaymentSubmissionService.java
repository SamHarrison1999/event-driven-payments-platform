package com.samharrison.payments.payment.internal;

import java.util.Objects;
import java.util.UUID;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;

@Service
public class PaymentSubmissionService {

    private final PaymentReservationCoordinator
        reservationCoordinator;

    private final PaymentProcessingCoordinator
        processingCoordinator;

    public PaymentSubmissionService(
        PaymentReservationCoordinator
            reservationCoordinator,
        PaymentProcessingCoordinator
            processingCoordinator
    ) {
        this.reservationCoordinator =
            Objects.requireNonNull(
                reservationCoordinator,
                "reservationCoordinator must not be null"
            );

        this.processingCoordinator =
            Objects.requireNonNull(
                processingCoordinator,
                "processingCoordinator must not be null"
            );
    }

    @PreAuthorize("hasRole('CUSTOMER')")
    public StoredPaymentResponse submit(
        UUID actorIdentityId,
        String rawIdempotencyKey,
        PaymentCreateRequest request
    ) {
        UUID requiredActorIdentityId =
            Objects.requireNonNull(
                actorIdentityId,
                "actorIdentityId must not be null"
            );

        PaymentCreateRequest requiredRequest =
            Objects.requireNonNull(
                request,
                "request must not be null"
            );

        PaymentRequestData domainRequest =
            requiredRequest.toDomain();

        IdempotencyKey idempotencyKey =
            PaymentIdempotencyHeader.parse(
                rawIdempotencyKey
            );

        PaymentReservationResult reservation =
            reservationCoordinator.reserve(
                requiredActorIdentityId,
                idempotencyKey,
                domainRequest
            );

        return switch (reservation) {
            case PaymentReservationResult.Acquired acquired ->
                processingCoordinator.process(
                    acquired.paymentId(),
                    acquired.ownerToken()
                );
            case PaymentReservationResult.Replay replay ->
                replay.response();
            case PaymentReservationResult.Conflict conflict ->
                throw new PaymentIdempotencyConflictException(
                    conflict.reason()
                );
        };
    }
}
