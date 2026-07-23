package com.samharrison.payments.outbox.internal;

record OutboxPublicationBatchResult(
    int claimed,
    int published,
    int retryScheduled,
    int deadLettered
) {
}
