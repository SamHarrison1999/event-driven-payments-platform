package com.samharrison.payments.payment.internal;

import jakarta.persistence.OptimisticLockException;
import java.sql.SQLException;
import java.util.Objects;
import java.util.UUID;
import org.springframework.dao.ConcurrencyFailureException;
import org.springframework.stereotype.Service;

@Service
class PaymentProcessingCoordinator {

    static final int MAX_POSTING_ATTEMPTS = 3;

    private static final String SERIALIZATION_FAILURE =
        "40001";

    private static final String DEADLOCK_DETECTED =
        "40P01";

    private final PaymentPostingTransaction postingTransaction;

    private final PaymentFailureFinalizer failureFinalizer;

    PaymentProcessingCoordinator(
        PaymentPostingTransaction postingTransaction,
        PaymentFailureFinalizer failureFinalizer
    ) {
        this.postingTransaction =
            Objects.requireNonNull(
                postingTransaction,
                "postingTransaction must not be null"
            );

        this.failureFinalizer =
            Objects.requireNonNull(
                failureFinalizer,
                "failureFinalizer must not be null"
            );
    }

    StoredPaymentResponse process(
        UUID paymentId,
        UUID ownerToken
    ) {
        UUID requiredPaymentId =
            Objects.requireNonNull(
                paymentId,
                "paymentId must not be null"
            );

        UUID requiredOwnerToken =
            Objects.requireNonNull(
                ownerToken,
                "ownerToken must not be null"
            );

        for (
            int attempt = 1;
            attempt <= MAX_POSTING_ATTEMPTS;
            attempt++
        ) {
            try {
                return postingTransaction.process(
                    requiredPaymentId,
                    requiredOwnerToken
                );
            } catch (RuntimeException exception) {
                if (!isConcurrencyFailure(exception)) {
                    return failureFinalizer
                        .finalizeFailure(
                            requiredPaymentId,
                            requiredOwnerToken,
                            PaymentFailureReason
                                .PROCESSING_FAILED
                        );
                }

                if (
                    attempt
                        == MAX_POSTING_ATTEMPTS
                ) {
                    return failureFinalizer
                        .finalizeFailure(
                            requiredPaymentId,
                            requiredOwnerToken,
                            PaymentFailureReason
                                .CONCURRENT_MODIFICATION
                        );
                }
            }
        }

        throw new IllegalStateException(
            "Payment posting attempts were exhausted "
                + "without a terminal result."
        );
    }

    private static boolean isConcurrencyFailure(
        Throwable failure
    ) {
        Throwable current = failure;

        while (current != null) {
            if (
                current
                    instanceof ConcurrencyFailureException
                || current
                    instanceof OptimisticLockException
            ) {
                return true;
            }

            if (current instanceof SQLException sql) {
                String sqlState = sql.getSQLState();

                if (
                    SERIALIZATION_FAILURE.equals(
                        sqlState
                    )
                    || DEADLOCK_DETECTED.equals(
                        sqlState
                    )
                ) {
                    return true;
                }
            }

            current = current.getCause();
        }

        return false;
    }
}
