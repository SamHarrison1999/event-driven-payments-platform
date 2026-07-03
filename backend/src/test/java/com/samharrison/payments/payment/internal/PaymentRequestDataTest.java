package com.samharrison.payments.payment.internal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.samharrison.payments.shared.GbpAmount;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class PaymentRequestDataTest {

    @Test
    void createsImmutablePositivePaymentRequest() {
        UUID sourceAccountId = UUID.randomUUID();
        UUID destinationAccountId =
            UUID.randomUUID();

        PaymentRequestData request =
            new PaymentRequestData(
                sourceAccountId,
                destinationAccountId,
                GbpAmount.ofMinorUnits(1_250L)
            );

        assertThat(request.sourceAccountId())
            .isEqualTo(sourceAccountId);
        assertThat(request.destinationAccountId())
            .isEqualTo(destinationAccountId);
        assertThat(request.amount())
            .isEqualTo(
                GbpAmount.ofMinorUnits(1_250L)
            );
    }

    @Test
    void rejectsSameSourceAndDestination() {
        UUID accountId = UUID.randomUUID();

        assertThatThrownBy(
            () ->
                new PaymentRequestData(
                    accountId,
                    accountId,
                    GbpAmount.ofMinorUnits(100L)
                )
        )
            .isInstanceOf(
                InvalidPaymentException.class
            )
            .hasMessageContaining(
                "must be different"
            );
    }

    @Test
    void rejectsZeroAmount() {
        assertThatThrownBy(
            () ->
                new PaymentRequestData(
                    UUID.randomUUID(),
                    UUID.randomUUID(),
                    GbpAmount.ZERO
                )
        )
            .isInstanceOf(
                InvalidPaymentException.class
            )
            .hasMessageContaining(
                "greater than zero"
            );
    }

    @Test
    void rejectsMissingFields() {
        UUID sourceAccountId = UUID.randomUUID();
        UUID destinationAccountId =
            UUID.randomUUID();
        GbpAmount amount =
            GbpAmount.ofMinorUnits(100L);

        assertThatThrownBy(
            () ->
                new PaymentRequestData(
                    null,
                    destinationAccountId,
                    amount
                )
        )
            .isInstanceOf(
                NullPointerException.class
            )
            .hasMessage(
                "sourceAccountId must not be null"
            );

        assertThatThrownBy(
            () ->
                new PaymentRequestData(
                    sourceAccountId,
                    null,
                    amount
                )
        )
            .isInstanceOf(
                NullPointerException.class
            )
            .hasMessage(
                "destinationAccountId must not be null"
            );

        assertThatThrownBy(
            () ->
                new PaymentRequestData(
                    sourceAccountId,
                    destinationAccountId,
                    null
                )
        )
            .isInstanceOf(
                NullPointerException.class
            )
            .hasMessage(
                "amount must not be null"
            );
    }
}
