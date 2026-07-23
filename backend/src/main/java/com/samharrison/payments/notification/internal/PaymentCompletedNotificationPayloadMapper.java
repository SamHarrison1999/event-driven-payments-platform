package com.samharrison.payments.notification.internal;

import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.ObjectReader;
import com.samharrison.payments.outbox.PublishedOutboxEvent;
import tools.jackson.core.JacksonException;
import java.util.Objects;
import org.springframework.stereotype.Component;

@Component
class PaymentCompletedNotificationPayloadMapper {

    private final ObjectReader reader;

    PaymentCompletedNotificationPayloadMapper(
        JsonMapper objectMapper
    ) {
        reader =
            Objects.requireNonNull(
                objectMapper,
                "objectMapper must not be null"
            )
                .readerFor(
                    PaymentCompletedNotificationPayload
                        .class
                )
                .with(
                    DeserializationFeature
                        .FAIL_ON_UNKNOWN_PROPERTIES
                );
    }

    PaymentCompletedNotificationPayload read(
        PublishedOutboxEvent event
    ) {
        PublishedOutboxEvent requiredEvent =
            Objects.requireNonNull(
                event,
                "event must not be null"
            );

        try {
            PaymentCompletedNotificationPayload payload =
                reader.readValue(
                    requiredEvent.payload()
                );

            return payload.validatedAgainst(
                requiredEvent
            );
        } catch (
            JacksonException
                | IllegalArgumentException failure
        ) {
            throw new InvalidNotificationEventException(
                "payment.completed.v1 payload is invalid",
                failure
            );
        }
    }
}
