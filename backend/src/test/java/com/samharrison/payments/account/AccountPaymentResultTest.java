package com.samharrison.payments.account;

import static com.samharrison.payments.account.AccountPaymentRejectionReason.INSUFFICIENT_FUNDS;
import static com.samharrison.payments.account.AccountPaymentResult.Status.APPROVED;
import static com.samharrison.payments.account.AccountPaymentResult.Status.REJECTED;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.samharrison.payments.shared.GbpAmount;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class AccountPaymentResultTest {

    private static final Instant UPDATED_AT =
        Instant.parse(
            "2026-07-03T14:00:00Z"
        );

    @Test
    void approvedResultContainsResultingProjections() {
        AccountPaymentProjection source =
            projection(
                GbpAmount.ofMinorUnits(750L),
                2L
            );

        AccountPaymentProjection destination =
            projection(
                GbpAmount.ofMinorUnits(1_250L),
                4L
            );

        AccountPaymentResult.Approved result =
            new AccountPaymentResult.Approved(
                source,
                destination
            );

        assertThat(result.status())
            .isEqualTo(APPROVED);

        assertThat(result.source())
            .isEqualTo(source);

        assertThat(result.destination())
            .isEqualTo(destination);
    }

    @Test
    void rejectedResultContainsOnlyStableReason() {
        AccountPaymentResult.Rejected result =
            new AccountPaymentResult.Rejected(
                INSUFFICIENT_FUNDS
            );

        assertThat(result.status())
            .isEqualTo(REJECTED);

        assertThat(result.reason())
            .isEqualTo(INSUFFICIENT_FUNDS);
    }

    @Test
    void approvedResultRequiresDistinctProjections() {
        AccountPaymentProjection projection =
            projection(
                GbpAmount.ofMinorUnits(1_000L),
                1L
            );

        assertThatThrownBy(
            () ->
                new AccountPaymentResult.Approved(
                    projection,
                    projection
                )
        )
            .isInstanceOf(
                IllegalArgumentException.class
            )
            .hasMessageContaining(
                "must be different"
            );
    }

    @Test
    void projectionRejectsNegativeVersion() {
        assertThatThrownBy(
            () ->
                new AccountPaymentProjection(
                    UUID.randomUUID(),
                    UUID.randomUUID(),
                    GbpAmount.ZERO,
                    UPDATED_AT,
                    -1L
                )
        )
            .isInstanceOf(
                IllegalArgumentException.class
            )
            .hasMessageContaining(
                "version"
            );
    }

    private static AccountPaymentProjection projection(
        GbpAmount balance,
        long version
    ) {
        return new AccountPaymentProjection(
            UUID.randomUUID(),
            UUID.randomUUID(),
            balance,
            UPDATED_AT,
            version
        );
    }
}
