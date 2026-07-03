package com.samharrison.payments.payment.internal;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Objects;
import java.util.regex.Pattern;

public record PaymentRequestFingerprint(
    String value
) {

    public static final String VERSION = "v1";
    public static final int HEX_LENGTH = 64;

    private static final Pattern VALID_VALUE =
        Pattern.compile("[0-9a-f]{64}");

    public PaymentRequestFingerprint {
        value = validate(value);
    }

    public static PaymentRequestFingerprint of(
        String rawValue
    ) {
        return new PaymentRequestFingerprint(
            rawValue
        );
    }

    public static PaymentRequestFingerprint from(
        PaymentRequestData request
    ) {
        PaymentRequestData requiredRequest =
            Objects.requireNonNull(
                request,
                "request must not be null"
            );

        String canonical =
            VERSION
                + "\nsourceAccountId="
                + requiredRequest.sourceAccountId()
                + "\ndestinationAccountId="
                + requiredRequest.destinationAccountId()
                + "\namountMinorUnits="
                + requiredRequest.amount().minorUnits()
                + "\n";

        return new PaymentRequestFingerprint(
            HexFormat.of().formatHex(
                sha256(canonical)
            )
        );
    }

    private static byte[] sha256(
        String canonical
    ) {
        try {
            return MessageDigest
                .getInstance("SHA-256")
                .digest(
                    canonical.getBytes(
                        StandardCharsets.UTF_8
                    )
                );
        } catch (
            NoSuchAlgorithmException exception
        ) {
            throw new IllegalStateException(
                "SHA-256 is not available.",
                exception
            );
        }
    }

    private static String validate(
        String rawValue
    ) {
        if (
            rawValue == null
                || !VALID_VALUE
                    .matcher(rawValue)
                    .matches()
        ) {
            throw new InvalidPaymentException(
                "Payment request fingerprint must "
                    + "be exactly "
                    + HEX_LENGTH
                    + " lowercase hexadecimal "
                    + "characters."
            );
        }

        return rawValue;
    }
}
