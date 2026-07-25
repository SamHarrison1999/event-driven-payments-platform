package com.samharrison.payments.reporting.internal;

import java.time.Duration;
import java.time.Instant;

record ReportWindow(
    Instant from,
    Instant to
) {

    private static final Duration MAXIMUM_WINDOW =
        Duration.ofDays(31);

    ReportWindow {
        if (from == null || to == null) {
            throw new InvalidReportQueryException(
                "from and to are required."
            );
        }

        if (!from.isBefore(to)) {
            throw new InvalidReportQueryException(
                "from must be earlier than to."
            );
        }

        if (
            Duration.between(from, to)
                .compareTo(MAXIMUM_WINDOW) > 0
        ) {
            throw new InvalidReportQueryException(
                "The report window must not exceed "
                    + "31 days."
            );
        }
    }
}
