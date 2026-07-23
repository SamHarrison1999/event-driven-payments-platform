package com.samharrison.payments.notification.internal;

import java.time.Instant;
import java.util.UUID;

public record NotificationResponse(
    UUID notificationId,
    UUID paymentId,
    long amountMinorUnits,
    String currency,
    Instant paymentCompletedAt,
    String status,
    Instant createdAt,
    Instant deliveredAt
) {

    static NotificationResponse from(
        Notification notification
    ) {
        return new NotificationResponse(
            notification.id(),
            notification.paymentId(),
            notification.amountMinorUnits(),
            notification.currency(),
            notification.paymentCompletedAt(),
            notification.status().name(),
            notification.createdAt(),
            notification.deliveredAt()
        );
    }
}
