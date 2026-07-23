package com.samharrison.payments.notification.internal;

import java.util.List;
import java.util.Objects;
import org.springframework.stereotype.Service;

@Service
class NotificationDeliveryProcessor {

    private final NotificationClaimingService
        claimingService;

    private final NotificationDeliveryFinalizer
        finalizer;

    private final NotificationTransport transport;

    NotificationDeliveryProcessor(
        NotificationClaimingService claimingService,
        NotificationDeliveryFinalizer finalizer,
        NotificationTransport transport
    ) {
        this.claimingService =
            Objects.requireNonNull(
                claimingService,
                "claimingService must not be null"
            );

        this.finalizer =
            Objects.requireNonNull(
                finalizer,
                "finalizer must not be null"
            );

        this.transport =
            Objects.requireNonNull(
                transport,
                "transport must not be null"
            );
    }

    public NotificationDeliveryBatchResult
        deliverNextBatch(
            int batchSize
        ) {
        List<NotificationDelivery> claimed =
            claimingService.claim(batchSize);

        int delivered = 0;
        int retryScheduled = 0;
        int deadLettered = 0;

        for (NotificationDelivery delivery : claimed) {
            try {
                transport.deliver(delivery);

                finalizer.markDelivered(
                    delivery.notificationId(),
                    delivery.ownerToken()
                );

                delivered++;
            } catch (
                PermanentNotificationDeliveryException
                    failure
            ) {
                finalizer.markFailed(
                    delivery.notificationId(),
                    delivery.ownerToken(),
                    failure,
                    true
                );

                deadLettered++;
            } catch (RuntimeException failure) {
                boolean deadLetter =
                    finalizer.markFailed(
                        delivery.notificationId(),
                        delivery.ownerToken(),
                        failure,
                        false
                    );

                if (deadLetter) {
                    deadLettered++;
                } else {
                    retryScheduled++;
                }
            }
        }

        return new NotificationDeliveryBatchResult(
            claimed.size(),
            delivered,
            retryScheduled,
            deadLettered
        );
    }
}
