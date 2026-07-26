package com.samharrison.payments.reconciliation;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;

public record ReconciliationReportQuery(
    Instant from,
    Instant to,
    int limit
) {

    private static final Duration MAXIMUM_WINDOW =
        Duration.ofDays(31);

    public ReconciliationReportQuery {
        Objects.requireNonNull(
            from,
            "from must not be null"
        );
        Objects.requireNonNull(
            to,
            "to must not be null"
        );

        if (!from.isBefore(to)) {
            throw new IllegalArgumentException(
                "from must be earlier than to"
            );
        }

        if (
            Duration.between(from, to)
                .compareTo(MAXIMUM_WINDOW) > 0
        ) {
            throw new IllegalArgumentException(
                "window must not exceed 31 days"
            );
        }

        if (limit < 1 || limit > 10_001) {
            throw new IllegalArgumentException(
                "limit must be between 1 and 10001"
            );
        }
    }
}
