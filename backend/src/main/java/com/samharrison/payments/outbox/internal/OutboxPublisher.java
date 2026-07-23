package com.samharrison.payments.outbox.internal;

import java.util.List;
import java.util.Objects;
import org.springframework.stereotype.Service;

@Service
class OutboxPublisher {

    private final OutboxClaimingService claimingService;
    private final OutboxPublicationFinalizer finalizer;
    private final OutboxTransport transport;

    OutboxPublisher(
        OutboxClaimingService claimingService,
        OutboxPublicationFinalizer finalizer,
        OutboxTransport transport
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

    public OutboxPublicationBatchResult publishNextBatch(
        int batchSize
    ) {
        List<OutboxPublication> claimed =
            claimingService.claim(batchSize);

        int published = 0;
        int retryScheduled = 0;
        int deadLettered = 0;

        for (OutboxPublication publication : claimed) {
            try {
                transport.publish(publication);

                finalizer.markPublished(
                    publication.eventId(),
                    publication.ownerToken()
                );

                published++;
            } catch (
                PermanentOutboxPublicationException failure
            ) {
                finalizer.markFailed(
                    publication.eventId(),
                    publication.ownerToken(),
                    failure,
                    true
                );

                deadLettered++;
            } catch (RuntimeException failure) {
                boolean deadLetter =
                    finalizer.markFailed(
                        publication.eventId(),
                        publication.ownerToken(),
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

        return new OutboxPublicationBatchResult(
            claimed.size(),
            published,
            retryScheduled,
            deadLettered
        );
    }
}
