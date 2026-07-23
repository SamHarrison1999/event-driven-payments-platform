package com.samharrison.payments.notification.internal;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

record NotificationDelivery(
    UUID notificationId,
    UUID recipientIdentityUserId,
    UUID paymentId,
    long amountMinorUnits,
    String currency,
    Instant paymentCompletedAt,
    UUID ownerToken
) {

    NotificationDelivery {
        notificationId =
            Objects.requireNonNull(
                notificationId,
                "notificationId must not be null"
            );

        recipientIdentityUserId =
            Objects.requireNonNull(
                recipientIdentityUserId,
                "recipientIdentityUserId must not be null"
            );

        paymentId =
            Objects.requireNonNull(
                paymentId,
                "paymentId must not be null"
            );

        if (amountMinorUnits < 1) {
            throw new IllegalArgumentException(
                "amountMinorUnits must be positive"
            );
        }

        if (!"GBP".equals(currency)) {
            throw new IllegalArgumentException(
                "currency must be GBP"
            );
        }

        paymentCompletedAt =
            Objects.requireNonNull(
                paymentCompletedAt,
                "paymentCompletedAt must not be null"
            );

        ownerToken =
            Objects.requireNonNull(
                ownerToken,
                "ownerToken must not be null"
            );
    }
}
