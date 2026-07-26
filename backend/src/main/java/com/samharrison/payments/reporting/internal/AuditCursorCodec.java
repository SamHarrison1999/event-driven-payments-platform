package com.samharrison.payments.reporting.internal;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.Base64;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.Set;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

@Component
final class AuditCursorCodec {

    private static final String VERSION = "v1";

    private static final Pattern EVENT_IDENTIFIER =
        Pattern.compile(
            "^(BUSINESS_AUDIT|IDENTITY_SECURITY|"
                + "OUTBOX_REPLAY|SETTLEMENT_RESOLUTION):"
                + "[0-9a-f]{8}-[0-9a-f]{4}-"
                + "[0-9a-f]{4}-[0-9a-f]{4}-"
                + "[0-9a-f]{12}$"
        );

    String encode(
        Instant occurredAt,
        String eventId,
        String filterFingerprint
    ) {
        String value =
            String.join(
                "\n",
                VERSION,
                occurredAt.toString(),
                eventId,
                filterFingerprint
            );

        return Base64
            .getUrlEncoder()
            .withoutPadding()
            .encodeToString(
                value.getBytes(StandardCharsets.UTF_8)
            );
    }

    Cursor decode(
        String encodedCursor,
        String expectedFilterFingerprint
    ) {
        if (encodedCursor == null) {
            return null;
        }

        try {
            String decoded =
                new String(
                    Base64
                        .getUrlDecoder()
                        .decode(encodedCursor),
                    StandardCharsets.UTF_8
                );
            String[] fields =
                decoded.split("\n", -1);

            if (
                fields.length != 4
                    || !VERSION.equals(fields[0])
                    || !EVENT_IDENTIFIER
                        .matcher(fields[2])
                        .matches()
                    || !MessageDigest.isEqual(
                        fields[3].getBytes(
                            StandardCharsets.UTF_8
                        ),
                        expectedFilterFingerprint
                            .getBytes(
                                StandardCharsets.UTF_8
                            )
                    )
            ) {
                throw invalidCursor();
            }

            return new Cursor(
                Instant.parse(fields[1]),
                fields[2]
            );
        }
        catch (
            IllegalArgumentException
                | DateTimeParseException failure
        ) {
            throw invalidCursor();
        }
    }

    String fingerprint(
        AuditSearchFilter filter,
        Set<AuditCategory> permittedCategories
    ) {
        String categories =
            permittedCategories
                .stream()
                .sorted(
                    Comparator.comparing(Enum::name)
                )
                .map(Enum::name)
                .reduce(
                    "",
                    (left, right) ->
                        left.isEmpty()
                            ? right
                            : left + "," + right
                );

        String canonical =
            String.join(
                "\n",
                value(filter.from()),
                value(filter.to()),
                value(filter.category()),
                value(filter.eventType()),
                value(filter.actorIdentityUserId()),
                value(filter.subjectType()),
                value(filter.subjectIdentifier()),
                value(filter.correlationIdentifier()),
                value(filter.source()),
                Integer.toString(filter.limit()),
                categories
            );

        try {
            byte[] digest =
                MessageDigest
                    .getInstance("SHA-256")
                    .digest(
                        canonical.getBytes(
                            StandardCharsets.UTF_8
                        )
                    );

            return HexFormat
                .of()
                .formatHex(digest);
        }
        catch (NoSuchAlgorithmException failure) {
            throw new IllegalStateException(
                "SHA-256 is unavailable.",
                failure
            );
        }
    }

    private static String value(Object value) {
        return value == null
            ? "-"
            : value.toString();
    }

    private static InvalidAuditQueryException
        invalidCursor() {
        return new InvalidAuditQueryException(
            "cursor is malformed or does not match "
                + "the active audit filters."
        );
    }

    record Cursor(
        Instant occurredAt,
        String eventId
    ) {
    }
}
