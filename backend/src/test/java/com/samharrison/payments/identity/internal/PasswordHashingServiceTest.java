package com.samharrison.payments.identity.internal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.password.PasswordEncoder;

class PasswordHashingServiceTest {

    private static final String VALID_PASSWORD =
        "this is a valid passphrase";

    private final PasswordHashingService service;

    PasswordHashingServiceTest() {
        PasswordPolicy passwordPolicy =
            new PasswordPolicy();

        PasswordEncoder passwordEncoder =
            new PasswordHashingConfiguration()
                .passwordEncoder();

        service = new PasswordHashingService(
            passwordPolicy,
            passwordEncoder
        );
    }

    @Test
    void hashesUsingTheVersionedPbkdf2Format() {
        String hash = service.hash(
            VALID_PASSWORD
        );

        assertThat(hash)
            .startsWith(
                "{"
                    + PasswordHashingConfiguration
                    .CURRENT_ENCODING_ID
                    + "}"
            );

        assertThat(hash)
            .doesNotContain(VALID_PASSWORD);
    }

    @Test
    void createsDifferentHashesForTheSamePassword() {
        String firstHash = service.hash(
            VALID_PASSWORD
        );

        String secondHash = service.hash(
            VALID_PASSWORD
        );

        assertThat(firstHash)
            .isNotEqualTo(secondHash);
    }

    @Test
    void matchesTheCorrectPasswordOnly() {
        String hash = service.hash(
            VALID_PASSWORD
        );

        assertThat(
            service.matches(
                VALID_PASSWORD,
                hash
            )
        )
            .isTrue();

        assertThat(
            service.matches(
                "this is the wrong passphrase",
                hash
            )
        )
            .isFalse();
    }

    @Test
    void matchesCanonicallyEquivalentUnicodeInput() {
        String decomposedPassword =
            "Cafe\u0301 is a long password";

        String composedPassword =
            "Caf\u00E9 is a long password";

        String hash = service.hash(
            decomposedPassword
        );

        assertThat(
            service.matches(
                composedPassword,
                hash
            )
        )
            .isTrue();
    }

    @Test
    void rejectsMissingOrUnsupportedStoredHashes() {
        assertThat(
            service.matches(
                VALID_PASSWORD,
                null
            )
        )
            .isFalse();

        assertThat(
            service.matches(
                VALID_PASSWORD,
                ""
            )
        )
            .isFalse();

        assertThat(
            service.matches(
                VALID_PASSWORD,
                "{unknown}value"
            )
        )
            .isFalse();
    }

    @Test
    void reportsCurrentHashesDoNotNeedUpgrade() {
        String hash = service.hash(
            VALID_PASSWORD
        );

        assertThat(
            service.needsUpgrade(hash)
        )
            .isFalse();
    }

    @Test
    void enforcesThePolicyBeforeHashing() {
        assertThatThrownBy(
            () -> service.hash("too short")
        )
            .isInstanceOf(
                PasswordPolicyException.class
            );
    }
}
