package com.samharrison.payments.outbox;

import java.util.List;

public interface PublishedOutboxEventReader {

    List<PublishedOutboxEvent> readAfter(
        PublishedOutboxCursor cursor,
        int requestedBatchSize
    );
}
