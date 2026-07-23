package com.samharrison.payments.outbox.internal;

import com.samharrison.payments.outbox.OutboxEventAppender;
import com.samharrison.payments.outbox.OutboxEventRequest;
import com.samharrison.payments.shared.infrastructure.web.CorrelationIdFilter;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Objects;
import java.util.UUID;
import org.slf4j.MDC;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
class OutboxEventAppenderService
    implements OutboxEventAppender {

    private final OutboxEventRepository repository;
    private final Clock clock;

    OutboxEventAppenderService(
        OutboxEventRepository repository,
        Clock clock
    ) {
        this.repository =
            Objects.requireNonNull(
                repository,
                "repository must not be null"
            );

        this.clock =
            Objects.requireNonNull(
                clock,
                "clock must not be null"
            );
    }

    @Override
    @Transactional
    public UUID append(
        OutboxEventRequest request
    ) {
        OutboxEvent event =
            OutboxEvent.pending(
                request,
                correlationIdentifier(),
                now()
            );

        repository.saveAndFlush(event);
        return event.id();
    }

    private static String correlationIdentifier() {
        String current =
            MDC.get(CorrelationIdFilter.MDC_KEY);

        if (current != null && !current.isBlank()) {
            return current;
        }

        return UUID.randomUUID().toString();
    }

    private Instant now() {
        return Instant
            .now(clock)
            .truncatedTo(ChronoUnit.MICROS);
    }
}
