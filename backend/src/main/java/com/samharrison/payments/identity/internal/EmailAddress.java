package com.samharrison.payments.identity.internal;

import java.util.Locale;

public final class EmailAddress {

    public static final int MAX_LENGTH = 320;

    private final String value;
    private final String normalizedValue;

    private EmailAddress(
        String value,
        String normalizedValue
    ) {
        this.value = value;
        this.normalizedValue = normalizedValue;
    }

    public static EmailAddress of(String rawValue) {
        if (rawValue == null) {
            throw new IllegalArgumentException(
                "Email address is required."
            );
        }

        String value = rawValue.strip();

        validate(value);

        return new EmailAddress(
            value,
            value.toLowerCase(Locale.ROOT)
        );
    }

    private static void validate(String value) {
        if (value.isBlank()) {
            throw new IllegalArgumentException(
                "Email address must not be blank."
            );
        }

        if (value.length() > MAX_LENGTH) {
            throw new IllegalArgumentException(
                "Email address must not exceed "
                    + MAX_LENGTH
                    + " characters."
            );
        }

        if (value.codePoints().anyMatch(
            Character::isWhitespace
        )) {
            throw new IllegalArgumentException(
                "Email address must not contain whitespace."
            );
        }

        int firstAt = value.indexOf('@');
        int lastAt = value.lastIndexOf('@');

        if (
            firstAt <= 0
                || firstAt != lastAt
                || firstAt == value.length() - 1
        ) {
            throw new IllegalArgumentException(
                "Email address must contain one @ character "
                    + "with text on both sides."
            );
        }
    }

    public String value() {
        return value;
    }

    public String normalizedValue() {
        return normalizedValue;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }

        if (!(other instanceof EmailAddress that)) {
            return false;
        }

        return normalizedValue.equals(
            that.normalizedValue
        );
    }

    @Override
    public int hashCode() {
        return normalizedValue.hashCode();
    }

    @Override
    public String toString() {
        return value;
    }
}
