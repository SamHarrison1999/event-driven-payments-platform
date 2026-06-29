package com.samharrison.payments.shared;

import java.math.BigDecimal;
import java.util.Objects;

public record GbpAmount(
    long minorUnits
) implements Comparable<GbpAmount> {

    public static final String CURRENCY_CODE =
        "GBP";

    public static final int SCALE = 2;

    public static final GbpAmount ZERO =
        new GbpAmount(0L);

    public GbpAmount {
        if (minorUnits < 0L) {
            throw new InvalidGbpAmountException(
                "GBP amount must not be negative."
            );
        }
    }

    public static GbpAmount ofMinorUnits(
        long minorUnits
    ) {
        return new GbpAmount(minorUnits);
    }

    public static GbpAmount ofMajorUnits(
        BigDecimal majorUnits
    ) {
        if (majorUnits == null) {
            throw new InvalidGbpAmountException(
                "GBP amount is required."
            );
        }

        try {
            return new GbpAmount(
                majorUnits
                    .movePointRight(SCALE)
                    .longValueExact()
            );
        } catch (ArithmeticException exception) {
            throw new InvalidGbpAmountException(
                "GBP amount must contain no more "
                    + "than two decimal places and "
                    + "fit within the supported range.",
                exception
            );
        }
    }

    public BigDecimal majorUnits() {
        return BigDecimal.valueOf(
            minorUnits,
            SCALE
        );
    }

    public boolean isZero() {
        return minorUnits == 0L;
    }

    public boolean isPositive() {
        return minorUnits > 0L;
    }

    public GbpAmount plus(
        GbpAmount other
    ) {
        GbpAmount required =
            Objects.requireNonNull(
                other,
                "other must not be null"
            );

        try {
            return new GbpAmount(
                Math.addExact(
                    minorUnits,
                    required.minorUnits
                )
            );
        } catch (ArithmeticException exception) {
            throw new InvalidGbpAmountException(
                "GBP amount exceeds the supported "
                    + "range.",
                exception
            );
        }
    }

    public GbpAmount minus(
        GbpAmount other
    ) {
        GbpAmount required =
            Objects.requireNonNull(
                other,
                "other must not be null"
            );

        if (required.minorUnits > minorUnits) {
            throw new InvalidGbpAmountException(
                "GBP amount subtraction must not "
                    + "produce a negative result."
            );
        }

        return new GbpAmount(
            minorUnits - required.minorUnits
        );
    }

    @Override
    public int compareTo(
        GbpAmount other
    ) {
        GbpAmount required =
            Objects.requireNonNull(
                other,
                "other must not be null"
            );

        return Long.compare(
            minorUnits,
            required.minorUnits
        );
    }

    @Override
    public String toString() {
        return CURRENCY_CODE
            + " "
            + majorUnits().toPlainString();
    }
}