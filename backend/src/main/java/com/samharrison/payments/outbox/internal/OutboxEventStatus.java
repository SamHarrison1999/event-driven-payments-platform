package com.samharrison.payments.outbox.internal;

enum OutboxEventStatus {
    PENDING,
    PUBLISHING,
    PUBLISHED,
    DEAD_LETTER
}
