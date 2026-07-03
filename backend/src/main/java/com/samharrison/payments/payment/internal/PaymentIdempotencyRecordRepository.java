package com.samharrison.payments.payment.internal;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

interface PaymentIdempotencyRecordRepository
    extends JpaRepository<
        PaymentIdempotencyRecord,
        UUID
    > {

    Optional<PaymentIdempotencyRecord>
    findByActorIdentityIdAndOperationAndIdempotencyKey(
        UUID actorIdentityId,
        PaymentOperation operation,
        String idempotencyKey
    );

    Optional<PaymentIdempotencyRecord>
    findByPaymentId(
        UUID paymentId
    );
}