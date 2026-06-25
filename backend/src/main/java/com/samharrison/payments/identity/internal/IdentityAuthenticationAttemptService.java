package com.samharrison.payments.identity.internal;

import java.time.Clock;
import java.time.Instant;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class IdentityAuthenticationAttemptService {

    private final IdentityUserRepository repository;
    private final Clock clock;
    private final IdentityLockoutPolicy policy;

    public IdentityAuthenticationAttemptService(
        IdentityUserRepository repository,
        Clock clock,
        IdentityLockoutPolicy policy
    ) {
        this.repository = repository;
        this.clock = clock;
        this.policy = policy;
    }

    @Transactional
    void prepareForAuthentication(String rawEmail) {
        String normalizedEmail =
            normalizeEmailOrNull(rawEmail);

        if (normalizedEmail == null) {
            return;
        }

        repository
            .findByNormalizedEmail(
                normalizedEmail
            )
            .ifPresent(
                user ->
                    user.releaseExpiredLock(
                        Instant.now(clock)
                    )
            );
    }

    @Transactional
    void recordFailure(String rawEmail) {
        String normalizedEmail =
            normalizeEmailOrNull(rawEmail);

        if (normalizedEmail == null) {
            return;
        }

        repository
            .findByNormalizedEmail(
                normalizedEmail
            )
            .ifPresent(
                user ->
                    user.recordFailedLogin(
                        Instant.now(clock),
                        policy
                    )
            );
    }

    @Transactional
    void recordSuccess(UUID userId) {
        IdentityUser user =
            repository
                .findById(userId)
                .orElseThrow(
                    () ->
                        new IllegalStateException(
                            "Authenticated identity user "
                                + "no longer exists."
                        )
                );

        user.recordSuccessfulLogin(
            Instant.now(clock)
        );
    }

    private static String normalizeEmailOrNull(
        String rawEmail
    ) {
        try {
            return EmailAddress
                .of(rawEmail)
                .normalizedValue();
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }
}
