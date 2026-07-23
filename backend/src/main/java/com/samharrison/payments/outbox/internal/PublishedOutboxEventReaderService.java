package com.samharrison.payments.outbox.internal;

import com.samharrison.payments.outbox.PublishedOutboxCursor;
import com.samharrison.payments.outbox.PublishedOutboxEvent;
import com.samharrison.payments.outbox.PublishedOutboxEventReader;
import java.util.List;
import java.util.Objects;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
class PublishedOutboxEventReaderService
    implements PublishedOutboxEventReader {

    private static final int MAX_BATCH_SIZE = 100;

    private final OutboxEventRepository repository;

    PublishedOutboxEventReaderService(
        OutboxEventRepository repository
    ) {
        this.repository =
            Objects.requireNonNull(
                repository,
                "repository must not be null"
            );
    }

    @Override
    @Transactional(readOnly = true)
    public List<PublishedOutboxEvent> readAfter(
        PublishedOutboxCursor cursor,
        int requestedBatchSize
    ) {
        PublishedOutboxCursor requiredCursor =
            Objects.requireNonNull(
                cursor,
                "cursor must not be null"
            );

        if (
            requestedBatchSize < 1
                || requestedBatchSize > MAX_BATCH_SIZE
        ) {
            throw new IllegalArgumentException(
                "requestedBatchSize must be between 1 and "
                    + MAX_BATCH_SIZE
            );
        }

        return repository
            .findPublishedAfter(
                requiredCursor.publishedAt(),
                requiredCursor.eventId(),
                requestedBatchSize
            )
            .stream()
            .map(
                PublishedOutboxEventReaderService
                    ::toPublishedEvent
            )
            .toList();
    }

    private static PublishedOutboxEvent toPublishedEvent(
        OutboxEvent event
    ) {
        return new PublishedOutboxEvent(
            event.id(),
            event.aggregateType(),
            event.aggregateId(),
            event.eventType(),
            event.schemaVersion(),
            event.payload(),
            event.correlationIdentifier(),
            event.causationIdentifier(),
            event.createdAt(),
            event.publishedAt()
        );
    }
}
