package com.samharrison.payments.reporting.internal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.samharrison.payments.payment.PaymentReportQuery;
import com.samharrison.payments.reconciliation.ReconciliationReportQuery;
import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class ReportWindowTest {

    private static final Instant FROM =
        Instant.parse("2026-07-01T00:00:00Z");

    @Test
    void acceptsExactThirtyOneDayWindow() {
        ReportWindow window =
            new ReportWindow(
                FROM,
                FROM.plus(Duration.ofDays(31))
            );

        assertThat(window.from()).isEqualTo(FROM);
    }

    @Test
    void rejectsMissingReversedAndOverlongWindows() {
        assertThatThrownBy(
            () -> new ReportWindow(null, FROM)
        )
            .isInstanceOf(
                InvalidReportQueryException.class
            )
            .hasMessageContaining("required");

        assertThatThrownBy(
            () -> new ReportWindow(FROM, FROM)
        )
            .isInstanceOf(
                InvalidReportQueryException.class
            )
            .hasMessageContaining("earlier");

        assertThatThrownBy(
            () ->
                new ReportWindow(
                    FROM,
                    FROM.plus(
                        Duration.ofDays(31)
                    ).plusSeconds(1)
                )
        )
            .isInstanceOf(
                InvalidReportQueryException.class
            )
            .hasMessageContaining("31 days");
    }

    @Test
    void sourceReaderQueriesRemainBounded() {
        Instant overlongTo =
            FROM.plus(Duration.ofDays(32));

        assertThatThrownBy(
            () ->
                new PaymentReportQuery(
                    FROM,
                    overlongTo,
                    10_001
                )
        )
            .isInstanceOf(
                IllegalArgumentException.class
            )
            .hasMessageContaining("31 days");

        assertThatThrownBy(
            () ->
                new ReconciliationReportQuery(
                    FROM,
                    FROM.plus(Duration.ofDays(1)),
                    10_002
                )
        )
            .isInstanceOf(
                IllegalArgumentException.class
            )
            .hasMessageContaining("10001");
    }
}
