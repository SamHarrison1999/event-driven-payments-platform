package com.samharrison.payments.outbox.internal;

import java.util.Objects;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(
    prefix = "platform.background-processing",
    name = "enabled",
    havingValue = "true"
)
class OutboxBackgroundProcessor {

    private static final int BATCH_SIZE = 100;

    private final OutboxPublisher publisher;

    OutboxBackgroundProcessor(
        OutboxPublisher publisher
    ) {
        this.publisher =
            Objects.requireNonNull(
                publisher,
                "publisher must not be null"
            );
    }

    @Scheduled(
        fixedDelayString =
            "${platform.background-processing.fixed-delay-ms:2000}"
    )
    public void publishPendingEvents() {
        publisher.publishNextBatch(
            BATCH_SIZE
        );
    }
}
