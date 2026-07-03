package com.samharrison.payments.payment.internal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class IdempotencyKeyTest {

    @Test
    void preservesOpaqueCaseSensitiveValue() {
        IdempotencyKey key =
            IdempotencyKey.of(
                "Order-ABC_123:retry.1"
            );

        assertThat(key.value())
            .isEqualTo(
                "Order-ABC_123:retry.1"
            );
    }

    @Test
    void acceptsMaximumLength() {
        String value =
            "a".repeat(
                IdempotencyKey.MAX_LENGTH
            );

        assertThat(
            IdempotencyKey.of(value).value()
        )
            .isEqualTo(value);
    }

    @Test
    void rejectsMissingOrOversizedValue() {
        assertThatThrownBy(
            () -> IdempotencyKey.of(null)
        )
            .isInstanceOf(
                InvalidPaymentException.class
            )
            .hasMessageContaining("required");

        assertThatThrownBy(
            () -> IdempotencyKey.of("")
        )
            .isInstanceOf(
                InvalidPaymentException.class
            )
            .hasMessageContaining("required");

        assertThatThrownBy(
            () ->
                IdempotencyKey.of(
                    "a".repeat(
                        IdempotencyKey.MAX_LENGTH
                            + 1
                    )
                )
        )
            .isInstanceOf(
                InvalidPaymentException.class
            )
            .hasMessageContaining(
                "must not exceed"
            );
    }

    @Test
    void rejectsWhitespaceControlAndNonAscii() {
        assertThatThrownBy(
            () ->
                IdempotencyKey.of(
                    "contains space"
                )
        )
            .isInstanceOf(
                InvalidPaymentException.class
            )
            .hasMessageContaining(
                "visible ASCII"
            );

        assertThatThrownBy(
            () ->
                IdempotencyKey.of(
                    "contains\tcontrol"
                )
        )
            .isInstanceOf(
                InvalidPaymentException.class
            )
            .hasMessageContaining(
                "visible ASCII"
            );

        assertThatThrownBy(
            () ->
                IdempotencyKey.of(
                    "caf\u00e9"
                )
        )
            .isInstanceOf(
                InvalidPaymentException.class
            )
            .hasMessageContaining(
                "visible ASCII"
            );
    }
}
