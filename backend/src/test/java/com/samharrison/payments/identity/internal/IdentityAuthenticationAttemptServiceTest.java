package com.samharrison.payments.identity.internal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class IdentityAuthenticationAttemptServiceTest {

    private static final Instant NOW =
        Instant.parse(
            "2026-06-25T15:30:00Z"
        );

    @Mock
    private IdentityUserRepository repository;

    private IdentityAuthenticationAttemptService service;
    private IdentityLockoutPolicy policy;
    private IdentityUser user;

    @BeforeEach
    void setUp() {
        policy =
            IdentityLockoutPolicy.standard();

        Clock clock =
            Clock.fixed(
                NOW,
                ZoneOffset.UTC
            );

        service =
            new IdentityAuthenticationAttemptService(
                repository,
                clock,
                policy
            );

        user =
            IdentityUser.registerCustomer(
                EmailAddress.of(
                    "Sam.Customer@Example.COM"
                ),
                "{test}temporary-password-hash",
                NOW.minusSeconds(600)
            );
    }

    @Test
    void recordsFailureForAnExistingUser() {
        when(
            repository.findByNormalizedEmail(
                "sam.customer@example.com"
            )
        )
            .thenReturn(Optional.of(user));

        service.recordFailure(
            " Sam.Customer@Example.COM "
        );

        verify(repository)
            .findByNormalizedEmail(
                "sam.customer@example.com"
            );

        assertThat(user.failedLoginAttempts())
            .isEqualTo(1);

        assertThat(user.status())
            .isEqualTo(
                IdentityUserStatus.ACTIVE
            );

        assertThat(user.updatedAt())
            .isEqualTo(NOW);
    }

    @Test
    void ignoresMalformedEmailWithoutQueryingRepository() {
        service.recordFailure(
            "not-an-email-address"
        );

        verifyNoInteractions(repository);

        assertThat(user.failedLoginAttempts())
            .isZero();
    }

    @Test
    void ignoresFailureForAnUnknownUser() {
        when(
            repository.findByNormalizedEmail(
                "unknown@example.com"
            )
        )
            .thenReturn(Optional.empty());

        service.recordFailure(
            "unknown@example.com"
        );

        verify(repository)
            .findByNormalizedEmail(
                "unknown@example.com"
            );
    }

    @Test
    void resetsFailuresAfterSuccessfulAuthentication() {
        user.recordFailedLogin(
            NOW.minusSeconds(2),
            policy
        );

        user.recordFailedLogin(
            NOW.minusSeconds(1),
            policy
        );

        when(repository.findById(user.id()))
            .thenReturn(Optional.of(user));

        service.recordSuccess(user.id());

        assertThat(user.failedLoginAttempts())
            .isZero();

        assertThat(user.lockedUntil())
            .isNull();

        assertThat(user.updatedAt())
            .isEqualTo(NOW);
    }

    @Test
    void rejectsSuccessForADeletedIdentityUser() {
        UUID missingUserId =
            UUID.fromString(
                "1fbb2460-758c-40e0-a488-8e60daaf8270"
            );

        when(repository.findById(missingUserId))
            .thenReturn(Optional.empty());

        assertThatThrownBy(
            () ->
                service.recordSuccess(
                    missingUserId
                )
        )
            .isInstanceOf(
                IllegalStateException.class
            )
            .hasMessageContaining(
                "no longer exists"
            );
    }

    @Test
    void releasesAnExpiredLockBeforeAuthentication() {
        Instant firstFailedAttempt =
            NOW.minusSeconds(16 * 60L);

        for (
            int attempt = 0;
            attempt < 5;
            attempt++
        ) {
            user.recordFailedLogin(
                firstFailedAttempt.plusSeconds(
                    attempt
                ),
                policy
            );
        }

        assertThat(user.status())
            .isEqualTo(
                IdentityUserStatus.LOCKED
            );

        assertThat(user.lockedUntil())
            .isBefore(NOW);

        when(
            repository.findByNormalizedEmail(
                "sam.customer@example.com"
            )
        )
            .thenReturn(Optional.of(user));

        service.prepareForAuthentication(
            " Sam.Customer@Example.COM "
        );

        verify(repository)
            .findByNormalizedEmail(
                "sam.customer@example.com"
            );

        assertThat(user.status())
            .isEqualTo(
                IdentityUserStatus.ACTIVE
            );

        assertThat(user.failedLoginAttempts())
            .isZero();

        assertThat(user.lockedUntil())
            .isNull();

        assertThat(user.updatedAt())
            .isEqualTo(NOW);
    }

    @Test
    void ignoresMalformedEmailWhenPreparingAuthentication() {
        service.prepareForAuthentication(
            "not-an-email-address"
        );

        verifyNoInteractions(repository);

        assertThat(user.status())
            .isEqualTo(
                IdentityUserStatus.ACTIVE
            );

        assertThat(user.failedLoginAttempts())
            .isZero();
    }
}
