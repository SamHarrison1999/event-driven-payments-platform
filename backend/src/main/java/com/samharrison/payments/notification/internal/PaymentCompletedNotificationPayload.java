package com.samharrison.payments.notification.internal;

import com.samharrison.payments.outbox.PublishedOutboxEvent;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

record PaymentCompletedNotificationPayload(
    UUID paymentId,
    UUID ledgerTransactionId,
    UUID actorIdentityId,
    UUID sourceAccountId,
    UUID destinationAccountId,
    long amountMinorUnits,
    String currency,
    Instant completedAt
) {

    private static final String AGGREGATE_TYPE =
        "payment";

    private static final String CURRENCY = "GBP";

    PaymentCompletedNotificationPayload {
        paymentId =
            Objects.requireNonNull(
                paymentId,
                "paymentId must not be null"
            );

        ledgerTransactionId =
            Objects.requireNonNull(
                ledgerTransactionId,
                "ledgerTransactionId must not be null"
            );

        actorIdentityId =
            Objects.requireNonNull(
                actorIdentityId,
                "actorIdentityId must not be null"
            );

        sourceAccountId =
            Objects.requireNonNull(
                sourceAccountId,
                "sourceAccountId must not be null"
            );

        destinationAccountId =
            Objects.requireNonNull(
                destinationAccountId,
                "destinationAccountId must not be null"
            );

        if (amountMinorUnits < 1) {
            throw new IllegalArgumentException(
                "amountMinorUnits must be positive"
            );
        }

        if (!CURRENCY.equals(currency)) {
            throw new IllegalArgumentException(
                "currency must be GBP"
            );
        }

        completedAt =
            Objects.requireNonNull(
                completedAt,
                "completedAt must not be null"
            );
    }

    PaymentCompletedNotificationPayload
        validatedAgainst(
            PublishedOutboxEvent event
        ) {
        PublishedOutboxEvent requiredEvent =
            Objects.requireNonNull(
                event,
                "event must not be null"
            );

        if (
            !AGGREGATE_TYPE.equals(
                requiredEvent.aggregateType()
            )
                || !paymentId.equals(
                    requiredEvent.aggregateId()
                )
        ) {
            throw new IllegalArgumentException(
                "Event aggregate does not match payment payload"
            );
        }

        return this;
    }
}
