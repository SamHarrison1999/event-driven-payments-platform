package com.samharrison.payments.notification.internal;

record NotificationConsumptionResult(
    int read,
    int created,
    int duplicates,
    int failed,
    int ignored
) {
}
