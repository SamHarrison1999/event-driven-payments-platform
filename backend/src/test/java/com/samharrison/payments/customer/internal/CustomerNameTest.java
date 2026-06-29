package com.samharrison.payments.customer.internal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class CustomerNameTest {

    @Test
    void trimsSurroundingWhitespace() {
        assertThat(
            CustomerName.of(
                "  Sam Example  "
            ).value()
        )
            .isEqualTo("Sam Example");
    }

    @Test
    void preservesValidInternalCharacters() {
        assertThat(
            CustomerName.of(
                "Anne-Marie O'Connor"
            ).value()
        )
            .isEqualTo(
                "Anne-Marie O'Connor"
            );
    }

    @Test
    void rejectsNullName() {
        assertThatThrownBy(
            () -> CustomerName.of(null)
        )
            .isInstanceOf(
                InvalidCustomerNameException.class
            )
            .hasMessage(
                "Customer name is required."
            );
    }

    @Test
    void rejectsBlankName() {
        assertThatThrownBy(
            () -> CustomerName.of("   ")
        )
            .isInstanceOf(
                InvalidCustomerNameException.class
            )
            .hasMessage(
                "Customer name is required."
            );
    }

    @Test
    void rejectsOversizedName() {
        String oversizedName =
            "a".repeat(
                CustomerName.MAX_LENGTH + 1
            );

        assertThatThrownBy(
            () ->
                CustomerName.of(
                    oversizedName
                )
        )
            .isInstanceOf(
                InvalidCustomerNameException.class
            );
    }

    @Test
    void rejectsControlCharacters() {
        assertThatThrownBy(
            () ->
                CustomerName.of(
                    "Sam\nExample"
                )
        )
            .isInstanceOf(
                InvalidCustomerNameException.class
            )
            .hasMessageContaining(
                "control characters"
            );
    }
}