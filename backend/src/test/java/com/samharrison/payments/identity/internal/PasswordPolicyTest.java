package com.samharrison.payments.identity.internal;

import static com.samharrison.payments.identity.internal.PasswordPolicyViolation.BLOCKLISTED;
import static com.samharrison.payments.identity.internal.PasswordPolicyViolation.REQUIRED;
import static com.samharrison.payments.identity.internal.PasswordPolicyViolation.TOO_LONG;
import static com.samharrison.payments.identity.internal.PasswordPolicyViolation.TOO_SHORT;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;

import org.junit.jupiter.api.Test;

class PasswordPolicyTest {

    private final PasswordPolicy passwordPolicy =
        new PasswordPolicy();

    @Test
    void acceptsALongPasswordWithoutCompositionRules() {
        String password = "onlylowercaseletters";

        assertThat(
            passwordPolicy.validateAndNormalize(
                password
            )
        )
            .isEqualTo(password);
    }

    @Test
    void preservesLeadingAndTrailingSpaces() {
        String password =
            "  this is a long passphrase  ";

        assertThat(
            passwordPolicy.validateAndNormalize(
                password
            )
        )
            .isEqualTo(password);
    }

    @Test
    void normalizesUnicodeToNfc() {
        String decomposedPassword =
            "Cafe\u0301 is a long password";

        String composedPassword =
            "Caf\u00E9 is a long password";

        assertThat(
            passwordPolicy.validateAndNormalize(
                decomposedPassword
            )
        )
            .isEqualTo(composedPassword);
    }

    @Test
    void rejectsMissingAndWhitespaceOnlyPasswords() {
        assertViolation(
            null,
            REQUIRED
        );

        assertViolation(
            " ".repeat(15),
            REQUIRED
        );
    }

    @Test
    void rejectsPasswordsShorterThanTheMinimum() {
        assertViolation(
            "a".repeat(
                PasswordPolicy.MINIMUM_LENGTH - 1
            ),
            TOO_SHORT
        );
    }

    @Test
    void rejectsPasswordsLongerThanTheMaximum() {
        assertViolation(
            "a".repeat(
                PasswordPolicy.MAXIMUM_LENGTH + 1
            ),
            TOO_LONG
        );
    }

    @Test
    void countsUnicodeCodePointsRatherThanUtf16Units() {
        String password =
            "\uD83D\uDD10".repeat(
                PasswordPolicy.MINIMUM_LENGTH
            );

        assertThat(password.length())
            .isEqualTo(
                PasswordPolicy.MINIMUM_LENGTH * 2
            );

        assertThat(
            passwordPolicy.validateAndNormalize(
                password
            )
        )
            .isEqualTo(password);
    }

    @Test
    void rejectsBlocklistedPasswordsCaseInsensitively() {
        assertViolation(
            "PASSWORDPASSWORD",
            BLOCKLISTED
        );
    }

    @Test
    void comparesTheBlocklistAgainstTheWholePassword() {
        String password =
            "my passwordpassword phrase";

        assertThat(
            passwordPolicy.validateAndNormalize(
                password
            )
        )
            .isEqualTo(password);
    }

    private void assertViolation(
        String password,
        PasswordPolicyViolation expectedViolation
    ) {
        PasswordPolicyException exception =
            catchThrowableOfType(
                PasswordPolicyException.class,
                () -> passwordPolicy
                    .validateAndNormalize(password)
            );

        assertThat(exception.violation())
            .isEqualTo(expectedViolation);

        if (
            password != null
                && !password.isEmpty()
        ) {
            assertThat(exception.getMessage())
                .doesNotContain(password);
        }
    }
}
