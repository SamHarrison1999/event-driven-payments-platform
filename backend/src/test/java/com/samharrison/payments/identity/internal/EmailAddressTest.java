package com.samharrison.payments.identity.internal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class EmailAddressTest {

    @Test
    void trimsAndNormalizesTheEmailAddress() {
        EmailAddress emailAddress = EmailAddress.of(
            "  Sam.Example@Example.COM  "
        );

        assertThat(emailAddress.value())
            .isEqualTo("Sam.Example@Example.COM");

        assertThat(emailAddress.normalizedValue())
            .isEqualTo("sam.example@example.com");
    }

    @Test
    void comparesEmailAddressesUsingNormalizedValues() {
        EmailAddress first = EmailAddress.of(
            "Sam.Example@Example.COM"
        );

        EmailAddress second = EmailAddress.of(
            "sam.example@example.com"
        );

        assertThat(first)
            .isEqualTo(second);

        assertThat(first.hashCode())
            .isEqualTo(second.hashCode());
    }

    @Test
    void rejectsMalformedAtCharacters() {
        assertThatThrownBy(
            () -> EmailAddress.of("example.com")
        )
            .isInstanceOf(
                IllegalArgumentException.class
            );

        assertThatThrownBy(
            () -> EmailAddress.of("@example.com")
        )
            .isInstanceOf(
                IllegalArgumentException.class
            );

        assertThatThrownBy(
            () -> EmailAddress.of("user@")
        )
            .isInstanceOf(
                IllegalArgumentException.class
            );

        assertThatThrownBy(
            () -> EmailAddress.of(
                "user@@example.com"
            )
        )
            .isInstanceOf(
                IllegalArgumentException.class
            );
    }

    @Test
    void rejectsMissingOrWhitespaceValues() {
        assertThatThrownBy(
            () -> EmailAddress.of(null)
        )
            .isInstanceOf(
                IllegalArgumentException.class
            );

        assertThatThrownBy(
            () -> EmailAddress.of("   ")
        )
            .isInstanceOf(
                IllegalArgumentException.class
            );

        assertThatThrownBy(
            () -> EmailAddress.of(
                "user name@example.com"
            )
        )
            .isInstanceOf(
                IllegalArgumentException.class
            );
    }

    @Test
    void rejectsAddressesLongerThanTheLimit() {
        String oversizedAddress =
            "a".repeat(310) + "@example.com";

        assertThat(oversizedAddress.length())
            .isGreaterThan(EmailAddress.MAX_LENGTH);

        assertThatThrownBy(
            () -> EmailAddress.of(
                oversizedAddress
            )
        )
            .isInstanceOf(
                IllegalArgumentException.class
            );
    }
}
