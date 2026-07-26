package com.samharrison.payments.shared.infrastructure.web;

import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Objects;

final class FixedWindowRateLimiter {

    private final int maxTrackedKeys;

    private final Map<String, Window> windows = new HashMap<>();

    FixedWindowRateLimiter(int maxTrackedKeys) {
        if (maxTrackedKeys < 1) {
            throw new IllegalArgumentException(
                "maxTrackedKeys must be positive"
            );
        }

        this.maxTrackedKeys = maxTrackedKeys;
    }

    synchronized Decision tryAcquire(
        String key,
        int maximumRequests,
        Duration window,
        Instant now
    ) {
        String requiredKey = Objects.requireNonNull(key, "key");
        Duration requiredWindow = Objects.requireNonNull(window, "window");
        Instant requiredNow = Objects.requireNonNull(now, "now");

        if (maximumRequests < 1) {
            throw new IllegalArgumentException(
                "maximumRequests must be positive"
            );
        }

        if (requiredWindow.isZero() || requiredWindow.isNegative()) {
            throw new IllegalArgumentException(
                "window must be positive"
            );
        }

        Window current = windows.get(requiredKey);

        if (current != null && !requiredNow.isBefore(current.expiresAt())) {
            windows.remove(requiredKey);
            current = null;
        }

        if (current == null) {
            removeExpired(requiredNow);

            if (windows.size() >= maxTrackedKeys) {
                return Decision.denied(1, 0);
            }

            current = new Window(
                requiredNow.plus(requiredWindow),
                0
            );
            windows.put(requiredKey, current);
        }

        if (current.count() >= maximumRequests) {
            return Decision.denied(
                retryAfterSeconds(current.expiresAt(), requiredNow),
                0
            );
        }

        Window updated = new Window(
            current.expiresAt(),
            current.count() + 1
        );
        windows.put(requiredKey, updated);

        return new Decision(
            true,
            0,
            Math.max(0, maximumRequests - updated.count())
        );
    }

    synchronized int trackedKeyCount() {
        return windows.size();
    }

    private void removeExpired(Instant now) {
        Iterator<Map.Entry<String, Window>> iterator =
            windows.entrySet().iterator();

        while (iterator.hasNext()) {
            if (!now.isBefore(iterator.next().getValue().expiresAt())) {
                iterator.remove();
            }
        }
    }

    private static long retryAfterSeconds(Instant expiresAt, Instant now) {
        long millis = Math.max(
            1,
            Duration.between(now, expiresAt).toMillis()
        );

        return Math.max(1, (millis + 999) / 1_000);
    }

    record Decision(
        boolean allowed,
        long retryAfterSeconds,
        int remaining
    ) {
        static Decision denied(long retryAfterSeconds, int remaining) {
            return new Decision(false, retryAfterSeconds, remaining);
        }
    }

    private record Window(Instant expiresAt, int count) {
    }
}
