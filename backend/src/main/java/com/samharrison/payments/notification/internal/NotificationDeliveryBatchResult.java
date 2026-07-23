package com.samharrison.payments.notification.internal;

record NotificationDeliveryBatchResult(
    int claimed,
    int delivered,
    int retryScheduled,
    int deadLettered
) {
}
