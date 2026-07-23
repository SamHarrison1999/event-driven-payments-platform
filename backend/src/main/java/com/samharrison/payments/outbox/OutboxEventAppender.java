package com.samharrison.payments.outbox;

import java.util.UUID;

public interface OutboxEventAppender {

    UUID append(OutboxEventRequest request);
}
