package com.samharrison.payments.shared.infrastructure.web;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class FixedWindowRateLimiterTest {

    private static final Instant START =
        Instant.parse("2026-07-26T12:00:00Z");

    @Test
    void permitsOnlyTheConfiguredNumberWithinOneWindow() {
        FixedWindowRateLimiter limiter =
            new FixedWindowRateLimiter(10);

        FixedWindowRateLimiter.Decision first = limiter.tryAcquire(
            "login|127.0.0.1",
            2,
            Duration.ofMinutes(1),
            START
        );
        FixedWindowRateLimiter.Decision second = limiter.tryAcquire(
            "login|127.0.0.1",
            2,
            Duration.ofMinutes(1),
            START.plusSeconds(1)
        );
        FixedWindowRateLimiter.Decision third = limiter.tryAcquire(
            "login|127.0.0.1",
            2,
            Duration.ofMinutes(1),
            START.plusSeconds(2)
        );

        assertThat(first.allowed()).isTrue();
        assertThat(first.remaining()).isEqualTo(1);
        assertThat(second.allowed()).isTrue();
        assertThat(second.remaining()).isZero();
        assertThat(third.allowed()).isFalse();
        assertThat(third.retryAfterSeconds()).isEqualTo(58);
    }

    @Test
    void startsANewWindowAfterExpiry() {
        FixedWindowRateLimiter limiter =
            new FixedWindowRateLimiter(10);

        limiter.tryAcquire(
            "payment|127.0.0.1",
            1,
            Duration.ofSeconds(10),
            START
        );

        FixedWindowRateLimiter.Decision afterExpiry =
            limiter.tryAcquire(
                "payment|127.0.0.1",
                1,
                Duration.ofSeconds(10),
                START.plusSeconds(10)
            );

        assertThat(afterExpiry.allowed()).isTrue();
        assertThat(afterExpiry.remaining()).isZero();
    }

    @Test
    void expiresIdleKeysBeforeRejectingANewKey() {
        FixedWindowRateLimiter limiter =
            new FixedWindowRateLimiter(1);

        limiter.tryAcquire(
            "expired",
            1,
            Duration.ofSeconds(1),
            START
        );

        FixedWindowRateLimiter.Decision newKey = limiter.tryAcquire(
            "new",
            1,
            Duration.ofSeconds(1),
            START.plusSeconds(1)
        );

        assertThat(newKey.allowed()).isTrue();
        assertThat(limiter.trackedKeyCount()).isEqualTo(1);
    }

    @Test
    void boundsTrackedKeysAndFailsClosedWhenTheBoundIsReached() {
        FixedWindowRateLimiter limiter =
            new FixedWindowRateLimiter(1);

        limiter.tryAcquire(
            "first",
            1,
            Duration.ofMinutes(1),
            START
        );

        FixedWindowRateLimiter.Decision second = limiter.tryAcquire(
            "second",
            1,
            Duration.ofMinutes(1),
            START
        );

        assertThat(second.allowed()).isFalse();
        assertThat(second.retryAfterSeconds()).isEqualTo(1);
        assertThat(limiter.trackedKeyCount()).isEqualTo(1);
    }
}
