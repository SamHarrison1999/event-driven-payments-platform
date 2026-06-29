package com.samharrison.payments.shared;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class GbpAmountTest {

    @Test
    void createsZeroAndConvertsMajorUnits() {
        GbpAmount amount =
            GbpAmount.ofMajorUnits(
                new BigDecimal("12.30")
            );

        assertThat(amount.minorUnits())
            .isEqualTo(1_230L);

        assertThat(amount.majorUnits())
            .isEqualByComparingTo("12.30");

        assertThat(amount.isPositive())
            .isTrue();

        assertThat(GbpAmount.ZERO.isZero())
            .isTrue();

        assertThat(amount.toString())
            .isEqualTo("GBP 12.30");
    }

    @Test
    void acceptsMajorUnitsWithFewerDecimals() {
        assertThat(
            GbpAmount
                .ofMajorUnits(
                    new BigDecimal("12.3")
                )
                .minorUnits()
        )
            .isEqualTo(1_230L);

        assertThat(
            GbpAmount
                .ofMajorUnits(
                    new BigDecimal("12")
                )
                .minorUnits()
        )
            .isEqualTo(1_200L);
    }

    @Test
    void rejectsMissingNegativeAndFractionalPennyValues() {
        assertThatThrownBy(
            () ->
                GbpAmount.ofMajorUnits(null)
        )
            .isInstanceOf(
                InvalidGbpAmountException.class
            );

        assertThatThrownBy(
            () ->
                GbpAmount.ofMinorUnits(-1L)
        )
            .isInstanceOf(
                InvalidGbpAmountException.class
            );

        assertThatThrownBy(
            () ->
                GbpAmount.ofMajorUnits(
                    new BigDecimal("1.001")
                )
        )
            .isInstanceOf(
                InvalidGbpAmountException.class
            );
    }

    @Test
    void addsAndSubtractsAmountsExactly() {
        GbpAmount first =
            GbpAmount.ofMinorUnits(1_250L);

        GbpAmount second =
            GbpAmount.ofMinorUnits(250L);

        assertThat(
            first.plus(second)
        )
            .isEqualTo(
                GbpAmount.ofMinorUnits(1_500L)
            );

        assertThat(
            first.minus(second)
        )
            .isEqualTo(
                GbpAmount.ofMinorUnits(1_000L)
            );
    }

    @Test
    void rejectsNegativeSubtractionAndOverflow() {
        assertThatThrownBy(
            () ->
                GbpAmount
                    .ofMinorUnits(100L)
                    .minus(
                        GbpAmount.ofMinorUnits(
                            101L
                        )
                    )
        )
            .isInstanceOf(
                InvalidGbpAmountException.class
            );

        assertThatThrownBy(
            () ->
                GbpAmount
                    .ofMinorUnits(
                        Long.MAX_VALUE
                    )
                    .plus(
                        GbpAmount.ofMinorUnits(
                            1L
                        )
                    )
        )
            .isInstanceOf(
                InvalidGbpAmountException.class
            );
    }

    @Test
    void comparesAmountsByMinorUnits() {
        GbpAmount smaller =
            GbpAmount.ofMinorUnits(99L);

        GbpAmount larger =
            GbpAmount.ofMinorUnits(100L);

        assertThat(smaller)
            .isLessThan(larger);

        assertThat(larger)
            .isGreaterThan(smaller);
    }
}