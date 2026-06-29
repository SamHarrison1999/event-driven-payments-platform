package com.samharrison.payments.account.internal;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

final class AccountVersionPrecondition {

    private static final Pattern STRONG_VERSION_ETAG =
        Pattern.compile(
            "\"(0|[1-9][0-9]*)\""
        );

    private AccountVersionPrecondition() {
    }

    static long parseRequired(
        String rawHeader
    ) {
        if (
            rawHeader == null
                || rawHeader.isBlank()
        ) {
            throw new
                AccountVersionPreconditionRequiredException();
        }

        String candidate = rawHeader.trim();

        Matcher matcher =
            STRONG_VERSION_ETAG.matcher(candidate);

        if (!matcher.matches()) {
            throw new
                InvalidAccountVersionPreconditionException(
                    rawHeader
                );
        }

        try {
            return Long.parseLong(
                matcher.group(1)
            );
        } catch (NumberFormatException exception) {
            throw new
                InvalidAccountVersionPreconditionException(
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