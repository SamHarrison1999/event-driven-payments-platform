package com.samharrison.payments.notification.internal;

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
class NotificationBackgroundProcessor {

    private static final int BATCH_SIZE = 100;

    private final NotificationEventConsumer
        eventConsumer;

    private final NotificationDeliveryProcessor
        deliveryProcessor;

    NotificationBackgroundProcessor(
        NotificationEventConsumer eventConsumer,
        NotificationDeliveryProcessor
            deliveryProcessor
    ) {
        this.eventConsumer =
            Objects.requireNonNull(
                eventConsumer,
                "eventConsumer must not be null"
            );

        this.deliveryProcessor =
            Objects.requireNonNull(
                deliveryProcessor,
                "deliveryProcessor must not be null"
            );
    }

    @Scheduled(
        fixedDelayString =
            "${platform.background-processing.fixed-delay-ms:2000}"
    )
    public void processNotifications() {
        eventConsumer.consumeNextBatch(
            BATCH_SIZE
        );

        deliveryProcessor.deliverNextBatch(
            BATCH_SIZE
        );
    }
}
