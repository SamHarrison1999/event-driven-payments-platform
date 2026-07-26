package com.samharrison.payments.reporting.internal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.Set;
import org.junit.jupiter.api.Test;

class AuditCursorCodecTest {

    private static final Instant OCCURRED_AT =
        Instant.parse(
            "2026-07-25T12:00:00Z"
        );

    private static final String EVENT_ID =
        "BUSINESS_AUDIT:"
            + "123e4567-e89b-12d3-a456-426614174000";

    private final AuditCursorCodec codec =
        new AuditCursorCodec();

    @Test
    void roundTripsCursorBoundToActiveFilters() {
        AuditSearchFilter filter = filter(10);
        String fingerprint =
            codec.fingerprint(
                filter,
                Set.of(
                    AuditCategory.CUSTOMER,
                    AuditCategory.ACCOUNT,
                    AuditCategory.PAYMENT
                )
            );

        String encoded =
            codec.encode(
                OCCURRED_AT,
                EVENT_ID,
                fingerprint
            );

        AuditCursorCodec.Cursor decoded =
            codec.decode(encoded, fingerprint);

        assertThat(decoded.occurredAt())
            .isEqualTo(OCCURRED_AT);
        assertThat(decoded.eventId())
            .isEqualTo(EVENT_ID);
    }

    @Test
    void rejectsCursorWhenFilterChanges() {
        AuditSearchFilter first = filter(10);
        AuditSearchFilter changed = filter(11);
        Set<AuditCategory> categories =
            Set.of(AuditCategory.PAYMENT);

        String encoded =
            codec.encode(
                OCCURRED_AT,
                EVENT_ID,
                codec.fingerprint(
                    first,
                    categories
                )
            );

        assertThatThrownBy(
            () ->
                codec.decode(
                    encoded,
                    codec.fingerprint(
                        changed,
                        categories
                    )
                )
        )
            .isInstanceOf(
                InvalidAuditQueryException.class
            );
    }

    private static AuditSearchFilter filter(
        int limit
    ) {
        return new AuditSearchFilter(
            Instant.parse(
                "2026-07-01T00:00:00Z"
            ),
            Instant.parse(
                "2026-07-02T00:00:00Z"
            ),
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            limit
        );
    }
}
