package com.samharrison.payments.notification.internal;

import com.samharrison.payments.outbox.PublishedOutboxCursor;
import com.samharrison.payments.outbox.PublishedOutboxEvent;
import com.samharrison.payments.outbox.PublishedOutboxEventReader;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Objects;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
class NotificationEventConsumer {

    static final String CONSUMER_NAME =
        "notification.payment-completed.v1";

    private static final String PAYMENT_COMPLETED =
        "payment.completed.v1";

    private static final int SUPPORTED_SCHEMA_VERSION =
        1;

    private static final int MAX_BATCH_SIZE = 100;

    private final PublishedOutboxEventReader eventReader;

    private final NotificationRepository
        notificationRepository;

    private final NotificationConsumerFailureRepository
        failureRepository;

    private final NotificationConsumerCheckpointRepository
        checkpointRepository;

    private final PaymentCompletedNotificationPayloadMapper
        payloadMapper;

    private final Clock clock;

    NotificationEventConsumer(
        PublishedOutboxEventReader eventReader,
        NotificationRepository notificationRepository,
        NotificationConsumerFailureRepository
            failureRepository,
        NotificationConsumerCheckpointRepository
            checkpointRepository,
        PaymentCompletedNotificationPayloadMapper
            payloadMapper,
        Clock clock
    ) {
        this.eventReader =
            Objects.requireNonNull(
                eventReader,
                "eventReader must not be null"
            );

        this.notificationRepository =
            Objects.requireNonNull(
                notificationRepository,
                "notificationRepository must not be null"
            );

        this.failureRepository =
            Objects.requireNonNull(
                failureRepository,
                "failureRepository must not be null"
            );

        this.checkpointRepository =
            Objects.requireNonNull(
                checkpointRepository,
                "checkpointRepository must not be null"
            );

        this.payloadMapper =
            Objects.requireNonNull(
                payloadMapper,
                "payloadMapper must not be null"
            );

        this.clock =
            Objects.requireNonNull(
                clock,
                "clock must not be null"
            );
    }

    @Transactional
    public NotificationConsumptionResult
        consumeNextBatch(
            int requestedBatchSize
        ) {
        validateBatchSize(requestedBatchSize);

        NotificationConsumerCheckpoint checkpoint =
            checkpointRepository
                .findForUpdate(CONSUMER_NAME)
                .orElseThrow(
                    () ->
                        new IllegalStateException(
                            "Notification checkpoint was not found"
                        )
                );

        List<PublishedOutboxEvent> events =
            eventReader.readAfter(
                new PublishedOutboxCursor(
                    checkpoint.lastPublishedAt(),
                    checkpoint.lastEventId()
                ),
                requestedBatchSize
            );

        int created = 0;
        int duplicates = 0;
        int failed = 0;
        int ignored = 0;

        for (PublishedOutboxEvent event : events) {
            if (
                !PAYMENT_COMPLETED.equals(
                    event.eventType()
                )
            ) {
                ignored++;
            } else if (
                event.schemaVersion()
                    != SUPPORTED_SCHEMA_VERSION
            ) {
                recordFailure(
                    event,
                    new InvalidNotificationEventException(
                        "Unsupported payment.completed.v1 schema version"
                    )
                );
                failed++;
            } else {
                ConsumptionOutcome outcome =
                    consumePaymentCompleted(event);

                switch (outcome) {
                    case CREATED -> created++;
                    case DUPLICATE -> duplicates++;
                    case FAILED -> failed++;
                }
            }

            checkpoint.advance(
                event.publishedAt(),
                event.eventId(),
                now()
            );
        }

        checkpointRepository.saveAndFlush(checkpoint);

        return new NotificationConsumptionResult(
            events.size(),
            created,
            duplicates,
            failed,
            ignored
        );
    }

    private ConsumptionOutcome consumePaymentCompleted(
        PublishedOutboxEvent event
    ) {
        try {
            PaymentCompletedNotificationPayload payload =
                payloadMapper.read(event);

            if (
                notificationRepository
                    .existsBySourceEventId(
                        event.eventId()
                    )
            ) {
                return ConsumptionOutcome.DUPLICATE;
            }

            notificationRepository.save(
                Notification.pending(
                    event.eventId(),
                    payload,
                    now()
                )
            );

            return ConsumptionOutcome.CREATED;
        } catch (
            InvalidNotificationEventException failure
        ) {
            recordFailure(event, failure);
            return ConsumptionOutcome.FAILED;
        }
    }

    private void recordFailure(
        PublishedOutboxEvent event,
        InvalidNotificationEventException failure
    ) {
        if (
            failureRepository
                .existsBySourceEventId(
                    event.eventId()
                )
        ) {
            return;
        }

        failureRepository.save(
            NotificationConsumerFailure.failed(
                event,
                failure,
                now()
            )
        );
    }

    private static void validateBatchSize(
        int requestedBatchSize
    ) {
        if (
            requestedBatchSize < 1
                || requestedBatchSize > MAX_BATCH_SIZE
        ) {
            throw new IllegalArgumentException(
                "requestedBatchSize must be between 1 and "
                    + MAX_BATCH_SIZE
            );
        }
    }

    private Instant now() {
        return Instant
            .now(clock)
            .truncatedTo(ChronoUnit.MICROS);
    }

    private enum ConsumptionOutcome {
        CREATED,
        DUPLICATE,
        FAILED
    }
}
