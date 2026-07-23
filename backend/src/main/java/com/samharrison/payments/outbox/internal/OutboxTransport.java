package com.samharrison.payments.outbox.internal;

interface OutboxTransport {

    void publish(OutboxPublication publication);
}
