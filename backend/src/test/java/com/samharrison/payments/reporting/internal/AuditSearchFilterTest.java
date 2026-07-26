package com.samharrison.payments.reporting.internal;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import org.junit.jupiter.api.Test;

class AuditSearchFilterTest {

    @Test
    void rejectsEmptySearch() {
        assertThatThrownBy(
            () ->
                new AuditSearchFilter(
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    50
                )
        )
            .isInstanceOf(
                InvalidAuditQueryException.class
            );
    }

    @Test
    void rejectsUnpairedSubjectFilter() {
        assertThatThrownBy(
            () ->
                new AuditSearchFilter(
                    null,
                    null,
                    AuditCategory.PAYMENT,
                    null,
                    null,
                    "payment",
                    null,
                    null,
                    null,
                    null,
                    50
                )
        )
            .isInstanceOf(
                InvalidAuditQueryException.class
            );
    }

    @Test
    void rejectsOverlongWindow() {
        assertThatThrownBy(
            () ->
                new AuditSearchFilter(
                    Instant.parse(
                        "2026-06-01T00:00:00Z"
                    ),
                    Instant.parse(
                        "2026-07-03T00:00:00Z"
                    ),
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    50
                )
        )
            .isInstanceOf(
                InvalidAuditQueryException.class
            );
    }
}
