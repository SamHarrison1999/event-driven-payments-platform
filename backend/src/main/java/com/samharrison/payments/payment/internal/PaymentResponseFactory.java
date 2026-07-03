package com.samharrison.payments.payment.internal;

import java.util.Objects;
import java.util.UUID;

final class PaymentResponseFactory {

    private static final int CREATED = 201;
    private static final int UNPROCESSABLE_CONTENT = 422;
    private static final int CONFLICT = 409;
    private static final int INTERNAL_SERVER_ERROR = 500;

    private PaymentResponseFactory() {
    }

    static StoredPaymentResponse completed(
        UUID paymentId,
        UUID ledgerTransactionId
    ) {
        UUID requiredPaymentId =
            Objects.requireNonNull(
                paymentId,
                "paymentId must not be null"
            );

        UUID requiredLedgerTransactionId =
            Objects.requireNonNull(
                ledgerTransactionId,
                "ledgerTransactionId must not be null"
            );

        String body =
            """
            {"paymentId":"%s","status":"COMPLETED","ledgerTransactionId":"%s"}
            """
                .strip()
                .formatted(
                    requiredPaymentId,
                    requiredLedgerTransactionId
                );

        return new StoredPaymentResponse(
            CREATED,
            StoredPaymentResponse.APPLICATION_JSON,
            body
        );
    }

    static StoredPaymentResponse rejected(
        UUID paymentId,
        PaymentRejectionReason reason
    ) {
        Objects.requireNonNull(
            paymentId,
            "paymentId must not be null"
        );

        PaymentRejectionReason requiredReason =
            Objects.requireNonNull(
                reason,
                "reason must not be null"
            );

        RejectionProblem problem =
            rejectionProblem(requiredReason);

        String body =
            """
            {"type":"urn:problem:payment:%s","title":"Payment rejected","status":422,"detail":"%s","code":"%s"}
            """
                .strip()
                .formatted(
                    problem.slug(),
                    problem.detail(),
                    requiredReason.code()
                );

        return new StoredPaymentResponse(
            UNPROCESSABLE_CONTENT,
            StoredPaymentResponse
                .APPLICATION_PROBLEM_JSON,
            body
        );
    }

    static StoredPaymentResponse failed(
        UUID paymentId,
        PaymentFailureReason reason
    ) {
        Objects.requireNonNull(
            paymentId,
            "paymentId must not be null"
        );

        PaymentFailureReason requiredReason =
            Objects.requireNonNull(
                reason,
                "reason must not be null"
            );

        FailureProblem problem =
            failureProblem(requiredReason);

        String body =
            """
            {"type":"urn:problem:payment:%s","title":"Payment processing failed","status":%d,"detail":"%s","code":"%s"}
            """
                .strip()
                .formatted(
                    problem.slug(),
                    problem.status(),
                    problem.detail(),
                    requiredReason.code()
                );

        return new StoredPaymentResponse(
            problem.status(),
            StoredPaymentResponse
                .APPLICATION_PROBLEM_JSON,
            body
        );
    }

    private static RejectionProblem rejectionProblem(
        PaymentRejectionReason reason
    ) {
        return switch (reason) {
            case SOURCE_NOT_OWNED ->
                new RejectionProblem(
                    "source-not-owned",
                    "The source account is not owned "
                        + "by the authenticated customer."
                );
            case SOURCE_NOT_FOUND ->
                new RejectionProblem(
                    "source-not-found",
                    "The source account was not found."
                );
            case DESTINATION_NOT_FOUND ->
                new RejectionProblem(
                    "destination-not-found",
                    "The destination account was not found."
                );
            case SOURCE_NOT_ACTIVE ->
                new RejectionProblem(
                    "source-not-active",
                    "The source account is not active."
                );
            case DESTINATION_NOT_ACTIVE ->
                new RejectionProblem(
                    "destination-not-active",
                    "The destination account is not active."
                );
            case CURRENCY_MISMATCH ->
                new RejectionProblem(
                    "currency-mismatch",
                    "Both accounts must use GBP."
                );
            case INSUFFICIENT_FUNDS ->
                new RejectionProblem(
                    "insufficient-funds",
                    "The source account has insufficient funds."
                );
        };
    }

    private static FailureProblem failureProblem(
        PaymentFailureReason reason
    ) {
        return switch (reason) {
            case PROCESSING_FAILED ->
                new FailureProblem(
                    "processing-failed",
                    INTERNAL_SERVER_ERROR,
                    "The payment could not be processed."
                );
            case CONCURRENT_MODIFICATION ->
                new FailureProblem(
                    "concurrent-modification",
                    CONFLICT,
                    "The payment could not be completed "
                        + "because the accounts changed concurrently."
                );
        };
    }

    private record RejectionProblem(
        String slug,
        String detail
    ) {
    }

    private record FailureProblem(
        String slug,
        int status,
        String detail
    ) {
    }
}
