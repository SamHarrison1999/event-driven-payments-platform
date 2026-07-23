package com.samharrison.payments.outbox.internal;

import static org.assertj.core.api.Assertions.assertThat;

import com.samharrison.payments.outbox.OutboxEventRequest;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class OutboxEventTest {

    private static final Instant CREATED_AT =
        Instant.parse("2026-07-06T12:00:00Z");

    @Test
    void movesThroughRetryAndPublicationLifecycle() {
        OutboxEvent event = event();
        UUID firstOwner = UUID.randomUUID();

        event.claim(
            firstOwner,
            CREATED_AT.plusSeconds(30),
            CREATED_AT
        );

        assertThat(event.status())
            .isEqualTo(
                OutboxEventStatus.PUBLISHING
            );
        assertThat(event.attemptCount())
            .isEqualTo(1);

        Instant failedAt =
            CREATED_AT.plusSeconds(1);
        Instant retryAt =
            CREATED_AT.plusSeconds(10);

        event.markFailure(
            firstOwner,
            "NETWORK",
            "Temporary outage",
            retryAt,
            failedAt,
            false
        );

        assertThat(event.status())
            .isEqualTo(
                OutboxEventStatus.PENDING
            );
        assertThat(event.nextAttemptAt())
            .isEqualTo(retryAt);

        UUID secondOwner = UUID.randomUUID();

        event.claim(
            secondOwner,
            retryAt.plusSeconds(30),
            retryAt
        );
        event.markPublished(
            secondOwner,
            retryAt.plusSeconds(1)
        );

        assertThat(event.status())
            .isEqualTo(
                OutboxEventStatus.PUBLISHED
            );
        assertThat(event.attemptCount())
            .isEqualTo(2);
        assertThat(event.publishedAt())
            .isEqualTo(
                retryAt.plusSeconds(1)
            );
    }

    @Test
    void permanentFailureMovesToDeadLetter() {
        OutboxEvent event = event();
        UUID owner = UUID.randomUUID();

        event.claim(
            owner,
            CREATED_AT.plusSeconds(30),
            CREATED_AT
        );

        event.markFailure(
            owner,
            "INVALID_EVENT",
            "Payload cannot be published",
            CREATED_AT.plusSeconds(5),
            CREATED_AT.plusSeconds(1),
            true
        );

        assertThat(event.status())
            .isEqualTo(
                OutboxEventStatus.DEAD_LETTER
            );
        assertThat(event.nextAttemptAt())
            .isNull();
    }

    private static OutboxEvent event() {
        UUID paymentId = UUID.randomUUID();

        return OutboxEvent.pending(
            new OutboxEventRequest(
                "payment",
                paymentId,
                "payment.completed.v1",
                1,
                """
                {"paymentId":"%s"}
                """
                    .strip()
                    .formatted(paymentId),
                paymentId.toString()
            ),
            "correlation-1",
            CREATED_AT
        );
    }
}
