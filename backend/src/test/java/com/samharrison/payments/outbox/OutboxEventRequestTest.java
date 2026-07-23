package com.samharrison.payments.outbox;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.UUID;
import org.junit.jupiter.api.Test;

class OutboxEventRequestTest {

    @Test
    void rejectsInvalidTypesAndPayloads() {
        UUID aggregateId = UUID.randomUUID();

        assertThatThrownBy(
            () ->
                new OutboxEventRequest(
                    "Payment",
                    aggregateId,
                    "payment.completed.v1",
                    1,
                    "{}",
                    null
                )
        )
            .isInstanceOf(
                IllegalArgumentException.class
            );

        assertThatThrownBy(
            () ->
                new OutboxEventRequest(
                    "payment",
                    aggregateId,
                    "payment.completed.v1",
                    0,
                    "{}",
                    null
                )
        )
            .isInstanceOf(
                IllegalArgumentException.class
            );

        assertThatThrownBy(
            () ->
                new OutboxEventRequest(
                    "payment",
                    aggregateId,
                    "payment.completed.v1",
                    1,
                    " ",
                    null
                )
        )
            .isInstanceOf(
                IllegalArgumentException.class
            );
    }
}
