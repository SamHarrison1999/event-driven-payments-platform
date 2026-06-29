package com.samharrison.payments.customer.internal;

public record CustomerName(String value) {

    public static final int MAX_LENGTH = 200;

    public CustomerName {
        value = normalize(value);
    }

    public static CustomerName of(
        String rawValue
    ) {
        return new CustomerName(rawValue);
    }

    private static String normalize(
        String rawValue
    ) {
        if (rawValue == null) {
            throw new InvalidCustomerNameException(
                "Customer name is required."
            );
        }

        String normalized = rawValue.strip();

        if (normalized.isEmpty()) {
            throw new InvalidCustomerNameException(
                "Customer name is required."
            );
        }

        if (normalized.length() > MAX_LENGTH) {
            throw new InvalidCustomerNameException(
                "Customer name must not exceed "
                    + MAX_LENGTH
                    + " characters."
            );
        }

        boolean containsControlCharacter =
            normalized
                .codePoints()
                .anyMatch(Character::isISOControl);

        if (containsControlCharacter) {
            throw new InvalidCustomerNameException(
                "Customer name must not contain "
                    + "control characters."
            );
        }

        return normalized;
    }
}