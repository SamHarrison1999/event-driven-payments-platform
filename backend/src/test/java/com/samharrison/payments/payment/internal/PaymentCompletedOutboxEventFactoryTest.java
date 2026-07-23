package com.samharrison.payments.payment.internal;

import static org.assertj.core.api.Assertions.assertThat;

import com.samharrison.payments.outbox.OutboxEventRequest;
import com.samharrison.payments.shared.GbpAmount;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class PaymentCompletedOutboxEventFactoryTest {

    @Test
    void createsStableVersionedNonSensitivePayload() {
        UUID actorId = UUID.randomUUID();
        UUID sourceAccountId = UUID.randomUUID();
        UUID destinationAccountId = UUID.randomUUID();
        UUID ledgerTransactionId = UUID.randomUUID();

        Instant createdAt =
            Instant.parse(
                "2026-07-06T12:00:00Z"
            );

        Instant completedAt =
            Instant.parse(
                "2026-07-06T12:00:30.123456Z"
            );

        PaymentRequestData request =
            new PaymentRequestData(
                sourceAccountId,
                destinationAccountId,
                GbpAmount.ofMinorUnits(12_345L)
            );

        Payment payment =
            Payment.pending(
                actorId,
                request,
                createdAt
            );

        payment.startProcessing(createdAt);
        payment.complete(
            ledgerTransactionId,
            completedAt
        );

        OutboxEventRequest event =
            PaymentCompletedOutboxEventFactory
                .create(
                    payment,
                    request,
                    ledgerTransactionId,
                    completedAt
                );

        assertThat(event.aggregateType())
            .isEqualTo("payment");
        assertThat(event.aggregateId())
            .isEqualTo(payment.id());
        assertThat(event.eventType())
            .isEqualTo("payment.completed.v1");
        assertThat(event.schemaVersion())
            .isEqualTo(1);
        assertThat(event.causationIdentifier())
            .isEqualTo(payment.id().toString());

        assertThat(event.payload())
            .isEqualTo(
                """
                {"paymentId":"%s","ledgerTransactionId":"%s","actorIdentityId":"%s","sourceAccountId":"%s","destinationAccountId":"%s","amountMinorUnits":12345,"currency":"GBP","completedAt":"2026-07-06T12:00:30.123456Z"}
                """
                    .strip()
                    .formatted(
                        payment.id(),
                        ledgerTransactionId,
                        actorId,
                        sourceAccountId,
                        destinationAccountId
                    )
            )
            .doesNotContain(
                "email",
                "password",
                "fullName"
            );
    }
}
