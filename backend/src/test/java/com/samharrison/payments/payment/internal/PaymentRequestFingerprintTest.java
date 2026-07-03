package com.samharrison.payments.payment.internal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.samharrison.payments.shared.GbpAmount;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class PaymentRequestFingerprintTest {

    private static final UUID SOURCE_ACCOUNT_ID =
        UUID.fromString(
            "11111111-1111-1111-1111-111111111111"
        );

    private static final UUID DESTINATION_ACCOUNT_ID =
        UUID.fromString(
            "22222222-2222-2222-2222-222222222222"
        );

    @Test
    void createsStableVersionedSha256Fingerprint() {
        PaymentRequestFingerprint fingerprint =
            PaymentRequestFingerprint.from(
                request(
                    SOURCE_ACCOUNT_ID,
                    DESTINATION_ACCOUNT_ID,
                    1_250L
                )
            );

        assertThat(fingerprint.value())
            .isEqualTo(
                "6ea9cbb890c9f4a9b7419d5e65c1ce3"
                    + "a4919c25326c3473ec07add1cda53760c"
            );
    }

    @Test
    void sameRequestProducesSameFingerprint() {
        PaymentRequestData request =
            request(
                SOURCE_ACCOUNT_ID,
                DESTINATION_ACCOUNT_ID,
                1_250L
            );

        assertThat(
            PaymentRequestFingerprint.from(
                request
            )
        )
            .isEqualTo(
                PaymentRequestFingerprint.from(
                    request
                )
            );
    }

    @Test
    void meaningfulFieldChangesProduceDifferentFingerprints() {
        PaymentRequestFingerprint original =
            PaymentRequestFingerprint.from(
                request(
                    SOURCE_ACCOUNT_ID,
                    DESTINATION_ACCOUNT_ID,
                    1_250L
                )
            );

        assertThat(
            PaymentRequestFingerprint.from(
                request(
                    UUID.randomUUID(),
                    DESTINATION_ACCOUNT_ID,
                    1_250L
                )
            )
        )
            .isNotEqualTo(original);

        assertThat(
            PaymentRequestFingerprint.from(
                request(
                    SOURCE_ACCOUNT_ID,
                    UUID.randomUUID(),
                    1_250L
                )
            )
        )
            .isNotEqualTo(original);

        assertThat(
            PaymentRequestFingerprint.from(
                request(
                    SOURCE_ACCOUNT_ID,
                    DESTINATION_ACCOUNT_ID,
                    1_251L
                )
            )
        )
            .isNotEqualTo(original);
    }

    @Test
    void validatesStoredFingerprintFormat() {
        assertThatThrownBy(
            () ->
                PaymentRequestFingerprint.of(
                    "ABC"
                )
        )
            .isInstanceOf(
                InvalidPaymentException.class
            )
            .hasMessageContaining(
                "64 lowercase hexadecimal"
            );

        assertThatThrownBy(
            () ->
                PaymentRequestFingerprint.of(
                    "A".repeat(64)
                )
        )
            .isInstanceOf(
                InvalidPaymentException.class
            )
            .hasMessageContaining(
                "lowercase hexadecimal"
            );
    }

    private static PaymentRequestData request(
        UUID sourceAccountId,
        UUID destinationAccountId,
        long amountMinorUnits
    ) {
        return new PaymentRequestData(
            sourceAccountId,
            destinationAccountId,
            GbpAmount.ofMinorUnits(
                amountMinorUnits
            )
        );
    }
}
