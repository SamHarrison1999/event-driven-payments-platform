package com.samharrison.payments.payment.internal;

import com.samharrison.payments.outbox.OutboxEventRequest;
import com.samharrison.payments.shared.GbpAmount;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

final class PaymentCompletedOutboxEventFactory {

    static final String AGGREGATE_TYPE = "payment";
    static final String EVENT_TYPE =
        "payment.completed.v1";
    static final int SCHEMA_VERSION = 1;

    private PaymentCompletedOutboxEventFactory() {
    }

    static OutboxEventRequest create(
        Payment payment,
        PaymentRequestData request,
        UUID ledgerTransactionId,
        Instant completedAt
    ) {
        Payment requiredPayment =
            Objects.requireNonNull(
                payment,
                "payment must not be null"
            );

        PaymentRequestData requiredRequest =
            Objects.requireNonNull(
                request,
                "request must not be null"
            );

        UUID requiredLedgerTransactionId =
            Objects.requireNonNull(
                ledgerTransactionId,
                "ledgerTransactionId must not be null"
            );

        Instant requiredCompletedAt =
            Objects.requireNonNull(
                completedAt,
                "completedAt must not be null"
            );

        String payload =
            """
            {"paymentId":"%s","ledgerTransactionId":"%s","actorIdentityId":"%s","sourceAccountId":"%s","destinationAccountId":"%s","amountMinorUnits":%d,"currency":"%s","completedAt":"%s"}
            """
                .strip()
                .formatted(
                    requiredPayment.id(),
                    requiredLedgerTransactionId,
                    requiredPayment.actorIdentityId(),
                    requiredRequest.sourceAccountId(),
                    requiredRequest.destinationAccountId(),
                    requiredRequest
                        .amount()
                        .minorUnits(),
                    GbpAmount.CURRENCY_CODE,
                    requiredCompletedAt
                );

        return new OutboxEventRequest(
            AGGREGATE_TYPE,
            requiredPayment.id(),
            EVENT_TYPE,
            SCHEMA_VERSION,
            payload,
            requiredPayment.id().toString()
        );
    }
}
