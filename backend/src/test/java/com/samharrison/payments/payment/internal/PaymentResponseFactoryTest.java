package com.samharrison.payments.payment.internal;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class PaymentResponseFactoryTest {

    private static final UUID PAYMENT_ID =
        UUID.fromString(
            "10000000-0000-0000-0000-000000000001"
        );

    private static final UUID LEDGER_TRANSACTION_ID =
        UUID.fromString(
            "20000000-0000-0000-0000-000000000001"
        );

    @Test
    void createsDeterministicCompletedResponse() {
        StoredPaymentResponse response =
            PaymentResponseFactory.completed(
                PAYMENT_ID,
                LEDGER_TRANSACTION_ID
            );

        assertThat(response)
            .isEqualTo(
                new StoredPaymentResponse(
                    201,
                    StoredPaymentResponse
                        .APPLICATION_JSON,
                    """
                    {"paymentId":"10000000-0000-0000-0000-000000000001","status":"COMPLETED","ledgerTransactionId":"20000000-0000-0000-0000-000000000001"}
                    """
                        .strip()
                )
            );
    }

    @ParameterizedTest
    @MethodSource("rejectionProblems")
    void createsDeterministicRejectionResponses(
        PaymentRejectionReason reason,
        String slug,
        String detail
    ) {
        StoredPaymentResponse response =
            PaymentResponseFactory.rejected(
                PAYMENT_ID,
                reason
            );

        String expectedBody =
            """
            {"type":"urn:problem:payment:%s","title":"Payment rejected","status":422,"detail":"%s","code":"%s"}
            """
                .strip()
                .formatted(
                    slug,
                    detail,
                    reason.code()
                );

        assertThat(response)
            .isEqualTo(
                new StoredPaymentResponse(
                    422,
                    StoredPaymentResponse
                        .APPLICATION_PROBLEM_JSON,
                    expectedBody
                )
            );
    }

    @Test
    void createsBoundedProcessingFailureResponse() {
        StoredPaymentResponse response =
            PaymentResponseFactory.failed(
                PAYMENT_ID,
                PaymentFailureReason
                    .PROCESSING_FAILED
            );

        assertThat(response)
            .isEqualTo(
                new StoredPaymentResponse(
                    500,
                    StoredPaymentResponse
                        .APPLICATION_PROBLEM_JSON,
                    """
                    {"type":"urn:problem:payment:processing-failed","title":"Payment processing failed","status":500,"detail":"The payment could not be processed.","code":"PAYMENT_PROCESSING_FAILED"}
                    """
                        .strip()
                )
            );
    }

    @Test
    void createsConcurrentModificationResponse() {
        StoredPaymentResponse response =
            PaymentResponseFactory.failed(
                PAYMENT_ID,
                PaymentFailureReason
                    .CONCURRENT_MODIFICATION
            );

        assertThat(response)
            .isEqualTo(
                new StoredPaymentResponse(
                    409,
                    StoredPaymentResponse
                        .APPLICATION_PROBLEM_JSON,
                    """
                    {"type":"urn:problem:payment:concurrent-modification","title":"Payment processing failed","status":409,"detail":"The payment could not be completed because the accounts changed concurrently.","code":"PAYMENT_CONCURRENT_MODIFICATION"}
                    """
                        .strip()
                )
            );
    }

    private static Stream<Arguments>
    rejectionProblems() {
        return Stream.of(
            Arguments.of(
                PaymentRejectionReason
                    .SOURCE_NOT_OWNED,
                "source-not-owned",
                "The source account is not owned "
                    + "by the authenticated customer."
            ),
            Arguments.of(
                PaymentRejectionReason
                    .SOURCE_NOT_FOUND,
                "source-not-found",
                "The source account was not found."
            ),
            Arguments.of(
                PaymentRejectionReason
                    .DESTINATION_NOT_FOUND,
                "destination-not-found",
                "The destination account was not found."
            ),
            Arguments.of(
                PaymentRejectionReason
                    .SOURCE_NOT_ACTIVE,
                "source-not-active",
                "The source account is not active."
            ),
            Arguments.of(
                PaymentRejectionReason
                    .DESTINATION_NOT_ACTIVE,
                "destination-not-active",
                "The destination account is not active."
            ),
            Arguments.of(
                PaymentRejectionReason
                    .CURRENCY_MISMATCH,
                "currency-mismatch",
                "Both accounts must use GBP."
            ),
            Arguments.of(
                PaymentRejectionReason
                    .INSUFFICIENT_FUNDS,
                "insufficient-funds",
                "The source account has insufficient funds."
            )
        );
    }
}
