package com.samharrison.payments.ledger.internal;

import java.util.Locale;
import java.util.regex.Pattern;

public record LedgerTransactionType(
    String value
) {

    public static final int MAX_LENGTH = 64;

    private static final Pattern VALID_VALUE =
        Pattern.compile("[A-Z][A-Z0-9_]*");

    public LedgerTransactionType {
        value = normalize(value);
    }

    public static LedgerTransactionType of(
        String rawValue
    ) {
        return new LedgerTransactionType(rawValue);
    }

    private static String normalize(
        String rawValue
    ) {
        if (rawValue == null) {
            throw new InvalidLedgerTransactionException(
                "Ledger transaction type is required."
            );
        }

        String normalized =
            rawValue
                .strip()
                .toUpperCase(Locale.ROOT);

        if (normalized.isEmpty()) {
            throw new InvalidLedgerTransactionException(
                "Ledger transaction type is required."
            );
        }

        if (normalized.length() > MAX_LENGTH) {
            throw new InvalidLedgerTransactionException(
                "Ledger transaction type must not exceed "
                    + MAX_LENGTH
                    + " characters."
            );
        }

        if (!VALID_VALUE.matcher(normalized).matches()) {
            throw new InvalidLedgerTransactionException(
                "Ledger transaction type must start with "
                    + "an uppercase letter and contain only "
                    + "uppercase letters, digits or underscores."
            );
        }

        return normalized;
    }
}