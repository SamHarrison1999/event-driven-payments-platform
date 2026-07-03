package com.samharrison.payments.payment.internal;

public record IdempotencyKey(
    String value
) {

    public static final int MAX_LENGTH = 128;

    public IdempotencyKey {
        value = validate(value);
    }

    public static IdempotencyKey of(
        String rawValue
    ) {
        return new IdempotencyKey(rawValue);
    }

    private static String validate(
        String rawValue
    ) {
        if (rawValue == null || rawValue.isEmpty()) {
            throw new InvalidPaymentException(
                "Idempotency key is required."
            );
        }

        if (rawValue.length() > MAX_LENGTH) {
            throw new InvalidPaymentException(
                "Idempotency key must not exceed "
                    + MAX_LENGTH
                    + " characters."
            );
        }

        boolean containsInvalidCharacter =
            rawValue
                .codePoints()
                .anyMatch(
                    codePoint ->
                        codePoint < 0x21
                            || codePoint > 0x7e
                );

        if (containsInvalidCharacter) {
            throw new InvalidPaymentException(
                "Idempotency key must contain only "
                    + "visible ASCII characters "
                    + "without whitespace."
            );
        }

        return rawValue;
    }
}
