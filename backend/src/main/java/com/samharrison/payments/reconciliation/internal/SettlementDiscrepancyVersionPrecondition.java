package com.samharrison.payments.reconciliation.internal;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

final class SettlementDiscrepancyVersionPrecondition {

    private static final Pattern STRONG_VERSION_ETAG =
        Pattern.compile(
            "\"(0|[1-9][0-9]*)\""
        );

    private SettlementDiscrepancyVersionPrecondition() {
    }

    static long parseRequired(
        String rawHeader
    ) {
        if (
            rawHeader == null
                || rawHeader.isBlank()
        ) {
            throw new
                SettlementDiscrepancyVersionRequiredException();
        }

        Matcher matcher =
            STRONG_VERSION_ETAG.matcher(
                rawHeader.trim()
            );

        if (!matcher.matches()) {
            throw new
                InvalidSettlementDiscrepancyVersionException(
                    rawHeader
                );
        }

        try {
            return Long.parseLong(
                matcher.group(1)
            );
        } catch (NumberFormatException exception) {
            throw new
                InvalidSettlementDiscrepancyVersionException(
                    rawHeader
                );
        }
    }

    static String format(
        long version
    ) {
        if (version < 0L) {
            throw new IllegalArgumentException(
                "version must not be negative"
            );
        }

        return "\"" + version + "\"";
    }
}
