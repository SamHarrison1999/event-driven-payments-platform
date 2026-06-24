package com.samharrison.payments.identity.internal;

import static com.samharrison.payments.identity.internal.PasswordPolicyViolation.BLOCKLISTED;
import static com.samharrison.payments.identity.internal.PasswordPolicyViolation.REQUIRED;
import static com.samharrison.payments.identity.internal.PasswordPolicyViolation.TOO_LONG;
import static com.samharrison.payments.identity.internal.PasswordPolicyViolation.TOO_SHORT;

import java.text.Normalizer;
import java.util.Locale;
import java.util.Set;
import org.springframework.stereotype.Component;

@Component
public final class PasswordPolicy {

    public static final int MINIMUM_LENGTH = 15;
    public static final int MAXIMUM_LENGTH = 128;

    private static final Set<String> BLOCKLIST =
        Set.of(
            "123456789012345",
            "aaaaaaaaaaaaaaa",
            "correcthorsebatterystaple",
            "event-driven-payments",
            "event-driven-payments-platform",
            "eventdrivenpayments",
            "letmeinletmeinletmein",
            "password123456",
            "passwordpassword",
            "payments-platform",
            "paymentsplatform",
            "qwertyqwertyqwerty"
        );

    public String validateAndNormalize(
        String rawPassword
    ) {
        if (rawPassword == null) {
            throw new PasswordPolicyException(
                REQUIRED,
                "Password is required."
            );
        }

        String normalizedPassword =
            normalizeForVerification(rawPassword);

        if (normalizedPassword.isBlank()) {
            throw new PasswordPolicyException(
                REQUIRED,
                "Password must contain a "
                    + "non-whitespace character."
            );
        }

        int codePointLength =
            normalizedPassword.codePointCount(
                0,
                normalizedPassword.length()
            );

        if (codePointLength < MINIMUM_LENGTH) {
            throw new PasswordPolicyException(
                TOO_SHORT,
                "Password must contain at least "
                    + MINIMUM_LENGTH
                    + " characters."
            );
        }

        if (codePointLength > MAXIMUM_LENGTH) {
            throw new PasswordPolicyException(
                TOO_LONG,
                "Password must not exceed "
                    + MAXIMUM_LENGTH
                    + " characters."
            );
        }

        String blocklistCandidate =
            normalizedPassword.toLowerCase(
                Locale.ROOT
            );

        if (BLOCKLIST.contains(blocklistCandidate)) {
            throw new PasswordPolicyException(
                BLOCKLISTED,
                "Choose a password that is not "
                    + "commonly used or expected."
            );
        }

        return normalizedPassword;
    }

    String normalizeForVerification(
        String rawPassword
    ) {
        return Normalizer.normalize(
            rawPassword,
            Normalizer.Form.NFC
        );
    }
}
