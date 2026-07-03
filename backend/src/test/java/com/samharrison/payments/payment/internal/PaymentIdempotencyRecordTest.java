package com.samharrison.payments.payment.internal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.samharrison.payments.shared.GbpAmount;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class PaymentIdempotencyRecordTest {

    private static final UUID ACTOR_ID =
        UUID.fromString(
            "11111111-1111-1111-1111-111111111111"
        );

    private static final UUID PAYMENT_ID =
        UUID.fromString(
            "22222222-2222-2222-2222-222222222222"
        );

    private static final UUID OWNER_TOKEN =
        UUID.fromString(
            "33333333-3333-3333-3333-333333333333"
        );

    private static final UUID NEW_OWNER_TOKEN =
        UUID.fromString(
            "44444444-4444-4444-4444-444444444444"
        );

    private static final Instant RESERVED_AT =
        Instant.parse("2026-07-03T09:00:00Z");

    @Test
    void reservesProcessingRecordWithBoundedLease() {
        PaymentIdempotencyRecord record =
            reservation();

        assertThat(record.actorIdentityId())
            .isEqualTo(ACTOR_ID);

        assertThat(record.operation())
            .isEqualTo(
                PaymentOperation.CREATE_INTERNAL_PAYMENT
            );

        assertThat(record.idempotencyKey())
            .isEqualTo(
                IdempotencyKey.of("payment-1001")
            );

        assertThat(record.paymentId())
            .isEqualTo(PAYMENT_ID);

        assertThat(record.status())
            .isEqualTo(
                PaymentIdempotencyStatus.PROCESSING
            );

        assertThat(record.processingOwnerToken())
            .isEqualTo(OWNER_TOKEN);

        assertThat(
            record.processingLeaseExpiresAt()
        )
            .isEqualTo(
                RESERVED_AT.plus(
                    PaymentIdempotencyRecord
                        .PROCESSING_LEASE
                )
            );

        assertThat(record.storedResponse())
            .isEmpty();

        assertThat(record.retentionExpiresAt())
            .isNull();

        assertThat(record.createdAt())
            .isEqualTo(RESERVED_AT);

        assertThat(record.updatedAt())
            .isEqualTo(RESERVED_AT);

        assertThat(record.version())
            .isZero();
    }

    @Test
    void evaluatesLeaseExpiryAtBoundary() {
        PaymentIdempotencyRecord record =
            reservation();

        Instant leaseExpiry =
            record.processingLeaseExpiresAt();

        assertThat(
            record.isLeaseExpired(
                leaseExpiry.minusNanos(1L)
            )
        )
            .isFalse();

        assertThat(
            record.isLeaseExpired(leaseExpiry)
        )
            .isTrue();
    }

    @Test
    void reclaimsExpiredLeaseWithNewOwner() {
        PaymentIdempotencyRecord record =
            reservation();

        Instant reclaimedAt =
            record.processingLeaseExpiresAt();

        record.reclaim(
            NEW_OWNER_TOKEN,
            reclaimedAt
        );

        assertThat(record.processingOwnerToken())
            .isEqualTo(NEW_OWNER_TOKEN);

        assertThat(
            record.processingLeaseExpiresAt()
        )
            .isEqualTo(
                reclaimedAt.plus(
                    PaymentIdempotencyRecord
                        .PROCESSING_LEASE
                )
            );

        assertThat(record.updatedAt())
            .isEqualTo(reclaimedAt);

        assertThat(record.isOwnedBy(OWNER_TOKEN))
            .isFalse();

        assertThat(
            record.isOwnedBy(NEW_OWNER_TOKEN)
        )
            .isTrue();
    }

    @Test
    void rejectsReclaimBeforeLeaseExpiry() {
        PaymentIdempotencyRecord record =
            reservation();

        assertThatThrownBy(
            () ->
                record.reclaim(
                    NEW_OWNER_TOKEN,
                    record
                        .processingLeaseExpiresAt()
                        .minusNanos(1L)
                )
        )
            .isInstanceOf(
                InvalidPaymentException.class
            )
            .hasMessageContaining(
                "cannot be reclaimed"
            );
    }

    @Test
    void completesOwnedRecordAndStoresResponse() {
        PaymentIdempotencyRecord record =
            reservation();

        Instant completedAt =
            RESERVED_AT.plusSeconds(30L);

        StoredPaymentResponse response =
            response();

        record.complete(
            OWNER_TOKEN,
            response,
            completedAt
        );

        assertThat(record.status())
            .isEqualTo(
                PaymentIdempotencyStatus.COMPLETED
            );

        assertThat(record.processingOwnerToken())
            .isNull();

        assertThat(
            record.processingLeaseExpiresAt()
        )
            .isNull();

        assertThat(record.storedResponse())
            .contains(response);

        assertThat(record.retentionExpiresAt())
            .isEqualTo(
                completedAt.plus(
                    PaymentIdempotencyRecord
                        .TERMINAL_RETENTION
                )
            );

        assertThat(record.updatedAt())
            .isEqualTo(completedAt);

        assertThat(record.isOwnedBy(OWNER_TOKEN))
            .isFalse();
    }

    @Test
    void rejectsCompletionByDifferentOwner() {
        PaymentIdempotencyRecord record =
            reservation();

        assertThatThrownBy(
            () ->
                record.complete(
                    NEW_OWNER_TOKEN,
                    response(),
                    RESERVED_AT.plusSeconds(30L)
                )
        )
            .isInstanceOf(
                InvalidPaymentException.class
            )
            .hasMessageContaining(
                "owner does not match"
            );

        assertThat(record.status())
            .isEqualTo(
                PaymentIdempotencyStatus.PROCESSING
            );

        assertThat(record.storedResponse())
            .isEmpty();
    }

    @Test
    void terminalRecordCannotCompleteOrReclaimAgain() {
        PaymentIdempotencyRecord record =
            reservation();

        record.complete(
            OWNER_TOKEN,
            response(),
            RESERVED_AT.plusSeconds(30L)
        );

        assertThatThrownBy(
            () ->
                record.complete(
                    OWNER_TOKEN,
                    response(),
                    RESERVED_AT.plusSeconds(60L)
                )
        )
            .isInstanceOf(
                InvalidPaymentStateTransitionException
                    .class
            );

        assertThatThrownBy(
            () ->
                record.reclaim(
                    NEW_OWNER_TOKEN,
                    RESERVED_AT.plusSeconds(60L)
                )
        )
            .isInstanceOf(
                InvalidPaymentStateTransitionException
                    .class
            );
    }

    @Test
    void comparesCanonicalFingerprint() {
        PaymentIdempotencyRecord record =
            reservation();

        assertThat(
            record.matches(fingerprint())
        )
            .isTrue();

        PaymentRequestData differentRequest =
            new PaymentRequestData(
                UUID.randomUUID(),
                UUID.randomUUID(),
                GbpAmount.ofMinorUnits(1_251L)
            );

        assertThat(
            record.matches(
                PaymentRequestFingerprint.from(
                    differentRequest
                )
            )
        )
            .isFalse();
    }

    @Test
    void rejectsBackwardsLifecycleTimestamp() {
        PaymentIdempotencyRecord record =
            reservation();

        assertThatThrownBy(
            () ->
                record.complete(
                    OWNER_TOKEN,
                    response(),
                    RESERVED_AT.minusSeconds(1L)
                )
        )
            .isInstanceOf(
                InvalidPaymentException.class
            )
            .hasMessageContaining(
                "previous update time"
            );
    }

    private static PaymentIdempotencyRecord
    reservation() {
        return PaymentIdempotencyRecord.reserve(
            ACTOR_ID,
            PaymentOperation.CREATE_INTERNAL_PAYMENT,
            IdempotencyKey.of("payment-1001"),
            fingerprint(),
            PAYMENT_ID,
            OWNER_TOKEN,
            RESERVED_AT
        );
    }

    private static PaymentRequestFingerprint
    fingerprint() {
        return PaymentRequestFingerprint.from(
            new PaymentRequestData(
                UUID.fromString(
                    "55555555-5555-5555-5555-555555555555"
                ),
                UUID.fromString(
                    "66666666-6666-6666-6666-666666666666"
                ),
                GbpAmount.ofMinorUnits(1_250L)
            )
        );
    }

    private static StoredPaymentResponse response() {
        return new StoredPaymentResponse(
            201,
            StoredPaymentResponse.APPLICATION_JSON,
            """
            {"paymentId":"22222222-2222-2222-2222-222222222222"}
            """.trim()
        );
    }
}