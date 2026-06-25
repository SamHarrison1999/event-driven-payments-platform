package com.samharrison.payments.identity.internal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class IdentityUserLockoutTest {

    private static final Instant REGISTERED_AT =
        Instant.parse(
            "2026-06-25T12:00:00Z"
        );

    private IdentityLockoutPolicy policy;
    private IdentityUser user;

    @BeforeEach
    void setUp() {
        policy =
            IdentityLockoutPolicy.standard();

        user =
            IdentityUser.registerCustomer(
                EmailAddress.of(
                    "sam.customer@example.com"
                ),
                "{test}temporary-password-hash",
                REGISTERED_AT
            );
    }

    @Test
    void definesAndValidatesTheStandardPolicy() {
        assertThat(
            policy.maximumFailedAttempts()
        )
            .isEqualTo(5);

        assertThat(policy.lockDuration())
            .isEqualTo(
                Duration.ofMinutes(15)
            );

        assertThatThrownBy(
            () ->
                new IdentityLockoutPolicy(
                    0,
                    Duration.ofMinutes(15)
                )
        )
            .isInstanceOf(
                IllegalArgumentException.class
            )
            .hasMessageContaining(
                "at least 1"
            );

        assertThatThrownBy(
            () ->
                new IdentityLockoutPolicy(
                    5,
                    Duration.ZERO
                )
        )
            .isInstanceOf(
                IllegalArgumentException.class
            )
            .hasMessageContaining(
                "positive"
            );
    }

    @Test
    void incrementsFailuresWithoutLockingBeforeThreshold() {
        Instant attemptedAt =
            REGISTERED_AT.plusSeconds(60);

        user.recordFailedLogin(
            attemptedAt,
            policy
        );

        assertThat(user.failedLoginAttempts())
            .isEqualTo(1);

        assertThat(user.status())
            .isEqualTo(
                IdentityUserStatus.ACTIVE
            );

        assertThat(user.lockedUntil())
            .isNull();

        assertThat(user.updatedAt())
            .isEqualTo(attemptedAt);
    }

    @Test
    void locksTheUserWhenTheThresholdIsReached() {
        Instant finalAttempt = null;

        for (
            int attempt = 1;
            attempt <= 5;
            attempt++
        ) {
            finalAttempt =
                REGISTERED_AT.plusSeconds(
                    attempt
                );

            user.recordFailedLogin(
                finalAttempt,
                policy
            );
        }

        assertThat(user.failedLoginAttempts())
            .isEqualTo(5);

        assertThat(user.status())
            .isEqualTo(
                IdentityUserStatus.LOCKED
            );

        assertThat(user.lockedUntil())
            .isEqualTo(
                finalAttempt.plus(
                    Duration.ofMinutes(15)
                )
            );
    }

    @Test
    void doesNotExtendAnExistingLock() {
        lockUser();

        Instant originalLockedUntil =
            user.lockedUntil();

        Instant originalUpdatedAt =
            user.updatedAt();

        user.recordFailedLogin(
            originalLockedUntil.minusSeconds(1),
            policy
        );

        assertThat(user.failedLoginAttempts())
            .isEqualTo(5);

        assertThat(user.lockedUntil())
            .isEqualTo(originalLockedUntil);

        assertThat(user.updatedAt())
            .isEqualTo(originalUpdatedAt);
    }

    @Test
    void releasesTheLockAtItsExpiryTime() {
        lockUser();

        Instant lockedUntil =
            user.lockedUntil();

        boolean releasedEarly =
            user.releaseExpiredLock(
                lockedUntil.minusSeconds(1)
            );

        assertThat(releasedEarly)
            .isFalse();

        assertThat(user.status())
            .isEqualTo(
                IdentityUserStatus.LOCKED
            );

        boolean releasedAtExpiry =
            user.releaseExpiredLock(
                lockedUntil
            );

        assertThat(releasedAtExpiry)
            .isTrue();

        assertThat(user.status())
            .isEqualTo(
                IdentityUserStatus.ACTIVE
            );

        assertThat(user.failedLoginAttempts())
            .isZero();

        assertThat(user.lockedUntil())
            .isNull();

        assertThat(user.updatedAt())
            .isEqualTo(lockedUntil);
    }

    @Test
    void resetsFailuresAfterSuccessfulLogin() {
        user.recordFailedLogin(
            REGISTERED_AT.plusSeconds(1),
            policy
        );

        user.recordFailedLogin(
            REGISTERED_AT.plusSeconds(2),
            policy
        );

        Instant authenticatedAt =
            REGISTERED_AT.plusSeconds(60);

        user.recordSuccessfulLogin(
            authenticatedAt
        );

        assertThat(user.failedLoginAttempts())
            .isZero();

        assertThat(user.lockedUntil())
            .isNull();

        assertThat(user.status())
            .isEqualTo(
                IdentityUserStatus.ACTIVE
            );

        assertThat(user.updatedAt())
            .isEqualTo(authenticatedAt);
    }

    private void lockUser() {
        for (
            int attempt = 1;
            attempt <= 5;
            attempt++
        ) {
            user.recordFailedLogin(
                REGISTERED_AT.plusSeconds(
                    attempt
                ),
                policy
            );
        }
    }
}
