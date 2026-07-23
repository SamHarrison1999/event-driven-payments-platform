package com.samharrison.payments.outbox.internal;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
class LoggingOutboxTransport
    implements OutboxTransport {

    private static final Logger LOGGER =
        LoggerFactory.getLogger(
            LoggingOutboxTransport.class
        );

    @Override
    public void publish(
        OutboxPublication publication
    ) {
        LOGGER.info(
            "Simulated outbox publication eventId={} "
                + "eventType={} aggregateId={} "
                + "correlationId={}",
            publication.eventId(),
            publication.eventType(),
            publication.aggregateId(),
            publication.correlationIdentifier()
        );
    }
}
