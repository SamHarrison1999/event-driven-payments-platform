package com.samharrison.payments.payment.internal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.samharrison.payments.shared.GbpAmount;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class PaymentTest {

    private static final Instant CREATED_AT =
        Instant.parse("2026-06-29T12:00:00Z");

    private static final Instant PROCESSING_AT =
        Instant.parse("2026-06-29T12:00:01Z");

    private static final Instant TERMINAL_AT =
        Instant.parse("2026-06-29T12:00:02Z");

    @Test
    void createsPendingPaymentWithImmutableRequest() {
        UUID actorIdentityId = UUID.randomUUID();
        PaymentRequestData request = request();

        Payment payment =
            Payment.pending(
                actorIdentityId,
                request,
                CREATED_AT
            );

        assertThat(payment.id()).isNotNull();
        assertThat(payment.actorIdentityId())
            .isEqualTo(actorIdentityId);
        assertThat(payment.request())
            .isSameAs(request);
        assertThat(payment.status())
            .isEqualTo(PaymentStatus.PENDING);
        assertThat(payment.status().isTerminal())
            .isFalse();
        assertThat(payment.ledgerTransactionId())
            .isNull();
        assertThat(payment.rejectionReason())
            .isNull();
        assertThat(payment.failureReason())
            .isNull();
        assertThat(payment.createdAt())
            .isEqualTo(CREATED_AT);
        assertThat(payment.updatedAt())
            .isEqualTo(CREATED_AT);
    }

    @Test
    void completesThroughProcessingWithLedgerReference() {
        Payment payment = pendingPayment();
        UUID ledgerTransactionId =
            UUID.randomUUID();

        payment.startProcessing(PROCESSING_AT);
        payment.complete(
            ledgerTransactionId,
            TERMINAL_AT
        );

        assertThat(payment.status())
            .isEqualTo(PaymentStatus.COMPLETED);
        assertThat(payment.status().isTerminal())
            .isTrue();
        assertThat(payment.ledgerTransactionId())
            .isEqualTo(ledgerTransactionId);
        assertThat(payment.rejectionReason())
            .isNull();
        assertThat(payment.failureReason())
            .isNull();
        assertThat(payment.updatedAt())
            .isEqualTo(TERMINAL_AT);
    }

    @Test
    void rejectsThroughProcessingWithoutLedgerReference() {
        Payment payment = pendingPayment();

        payment.startProcessing(PROCESSING_AT);
        payment.reject(
            PaymentRejectionReason
                .INSUFFICIENT_FUNDS,
            TERMINAL_AT
        );

        assertThat(payment.status())
            .isEqualTo(PaymentStatus.REJECTED);
        assertThat(payment.status().isTerminal())
            .isTrue();
        assertThat(payment.ledgerTransactionId())
            .isNull();
        assertThat(payment.rejectionReason())
            .isEqualTo(
                PaymentRejectionReason
                    .INSUFFICIENT_FUNDS
            );
        assertThat(payment.failureReason())
            .isNull();
    }

    @Test
    void failsThroughProcessingWithoutLedgerReference() {
        Payment payment = pendingPayment();

        payment.startProcessing(PROCESSING_AT);
        payment.fail(
            PaymentFailureReason
                .CONCURRENT_MODIFICATION,
            TERMINAL_AT
        );

        assertThat(payment.status())
            .isEqualTo(PaymentStatus.FAILED);
        assertThat(payment.status().isTerminal())
            .isTrue();
        assertThat(payment.ledgerTransactionId())
            .isNull();
        assertThat(payment.rejectionReason())
            .isNull();
        assertThat(payment.failureReason())
            .isEqualTo(
                PaymentFailureReason
                    .CONCURRENT_MODIFICATION
            );
    }

    @Test
    void rejectsDirectTerminalTransitionFromPending() {
        Payment payment = pendingPayment();

        assertThatThrownBy(
            () ->
                payment.complete(
                    UUID.randomUUID(),
                    TERMINAL_AT
                )
        )
            .isInstanceOf(
                InvalidPaymentStateTransitionException.class
            )
            .hasMessageContaining(
                "PENDING to COMPLETED"
            );

        assertThat(payment.status())
            .isEqualTo(PaymentStatus.PENDING);
        assertThat(payment.ledgerTransactionId())
            .isNull();
    }

    @Test
    void terminalStateCannotTransitionAgain() {
        Payment payment = pendingPayment();

        payment.startProcessing(PROCESSING_AT);
        payment.reject(
            PaymentRejectionReason
                .SOURCE_NOT_ACTIVE,
            TERMINAL_AT
        );

        assertThatThrownBy(
            () ->
                payment.startProcessing(
                    TERMINAL_AT.plusSeconds(1L)
                )
        )
            .isInstanceOf(
                InvalidPaymentStateTransitionException.class
            )
            .hasMessageContaining(
                "REJECTED to PROCESSING"
            );
    }

    @Test
    void requiresTerminalDetails() {
        Payment payment = pendingPayment();
        payment.startProcessing(PROCESSING_AT);

        assertThatThrownBy(
            () ->
                payment.complete(
                    null,
                    TERMINAL_AT
                )
        )
            .isInstanceOf(
                NullPointerException.class
            )
            .hasMessageContaining(
                "postedLedgerTransactionId"
            );

        assertThatThrownBy(
            () ->
                payment.reject(
                    null,
                    TERMINAL_AT
                )
        )
            .isInstanceOf(
                NullPointerException.class
            )
            .hasMessage(
                "reason must not be null"
            );

        assertThatThrownBy(
            () ->
                payment.fail(
                    null,
                    TERMINAL_AT
                )
        )
            .isInstanceOf(
                NullPointerException.class
            )
            .hasMessage(
                "reason must not be null"
            );

        assertThat(payment.status())
            .isEqualTo(PaymentStatus.PROCESSING);
    }

    @Test
    void rejectsTimeBeforePreviousUpdate() {
        Payment payment = pendingPayment();
        payment.startProcessing(PROCESSING_AT);

        assertThatThrownBy(
            () ->
                payment.reject(
                    PaymentRejectionReason
                        .SOURCE_NOT_ACTIVE,
                    CREATED_AT
                )
        )
            .isInstanceOf(
                InvalidPaymentException.class
            )
            .hasMessageContaining(
                "before the previous update time"
            );

        assertThat(payment.status())
            .isEqualTo(PaymentStatus.PROCESSING);
        assertThat(payment.rejectionReason())
            .isNull();
    }

    @Test
    void exposesStableBusinessCodes() {
        assertThat(
            PaymentRejectionReason
                .SOURCE_NOT_OWNED
                .code()
        )
            .isEqualTo(
                "PAYMENT_SOURCE_NOT_OWNED"
            );

        assertThat(
            PaymentFailureReason
                .PROCESSING_FAILED
                .code()
        )
            .isEqualTo(
                "PAYMENT_PROCESSING_FAILED"
            );

        assertThat(
            PaymentOperation
                .CREATE_INTERNAL_PAYMENT
                .name()
        )
            .isEqualTo(
                "CREATE_INTERNAL_PAYMENT"
            );
    }

    private static Payment pendingPayment() {
        return Payment.pending(
            UUID.randomUUID(),
            request(),
            CREATED_AT
        );
    }

    private static PaymentRequestData request() {
        return new PaymentRequestData(
            UUID.randomUUID(),
            UUID.randomUUID(),
            GbpAmount.ofMinorUnits(1_250L)
        );
    }
}
