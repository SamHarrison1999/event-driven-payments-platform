package com.samharrison.payments.payment.internal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.OptimisticLockingFailureException;

@ExtendWith(MockitoExtension.class)
class PaymentProcessingCoordinatorTest {

    private static final UUID PAYMENT_ID =
        UUID.fromString(
            "10000000-0000-0000-0000-000000000001"
        );

    private static final UUID OWNER_TOKEN =
        UUID.fromString(
            "20000000-0000-0000-0000-000000000001"
        );

    @Mock
    private PaymentPostingTransaction postingTransaction;

    @Mock
    private PaymentFailureFinalizer failureFinalizer;

    private PaymentProcessingCoordinator coordinator;

    @BeforeEach
    void setUp() {
        coordinator =
            new PaymentProcessingCoordinator(
                postingTransaction,
                failureFinalizer
            );
    }

    @Test
    void returnsSuccessfulPostingResponse() {
        StoredPaymentResponse response =
            successResponse();

        when(
            postingTransaction.process(
                PAYMENT_ID,
                OWNER_TOKEN
            )
        )
            .thenReturn(response);

        assertThat(
            coordinator.process(
                PAYMENT_ID,
                OWNER_TOKEN
            )
        )
            .isSameAs(response);

        verifyNoInteractions(failureFinalizer);
    }

    @Test
    void retriesConcurrencyFailureUpToSuccess() {
        StoredPaymentResponse response =
            successResponse();

        when(
            postingTransaction.process(
                PAYMENT_ID,
                OWNER_TOKEN
            )
        )
            .thenThrow(
                new OptimisticLockingFailureException(
                    "first conflict"
                )
            )
            .thenThrow(
                new OptimisticLockingFailureException(
                    "second conflict"
                )
            )
            .thenReturn(response);

        assertThat(
            coordinator.process(
                PAYMENT_ID,
                OWNER_TOKEN
            )
        )
            .isSameAs(response);

        verify(
            postingTransaction,
            times(3)
        )
            .process(
                PAYMENT_ID,
                OWNER_TOKEN
            );

        verifyNoInteractions(failureFinalizer);
    }

    @Test
    void finalisesPersistentConcurrencyFailure() {
        StoredPaymentResponse response =
            concurrentFailureResponse();

        when(
            postingTransaction.process(
                PAYMENT_ID,
                OWNER_TOKEN
            )
        )
            .thenThrow(
                new OptimisticLockingFailureException(
                    "persistent conflict"
                )
            );

        when(
            failureFinalizer.finalizeFailure(
                PAYMENT_ID,
                OWNER_TOKEN,
                PaymentFailureReason
                    .CONCURRENT_MODIFICATION
            )
        )
            .thenReturn(response);

        assertThat(
            coordinator.process(
                PAYMENT_ID,
                OWNER_TOKEN
            )
        )
            .isSameAs(response);

        verify(
            postingTransaction,
            times(
                PaymentProcessingCoordinator
                    .MAX_POSTING_ATTEMPTS
            )
        )
            .process(
                PAYMENT_ID,
                OWNER_TOKEN
            );
    }

    @Test
    void finalisesUnexpectedTechnicalFailure() {
        StoredPaymentResponse response =
            processingFailureResponse();

        when(
            postingTransaction.process(
                PAYMENT_ID,
                OWNER_TOKEN
            )
        )
            .thenThrow(
                new IllegalStateException(
                    "sensitive database detail"
                )
            );

        when(
            failureFinalizer.finalizeFailure(
                PAYMENT_ID,
                OWNER_TOKEN,
                PaymentFailureReason
                    .PROCESSING_FAILED
            )
        )
            .thenReturn(response);

        assertThat(
            coordinator.process(
                PAYMENT_ID,
                OWNER_TOKEN
            )
        )
            .isSameAs(response);

        verify(
            postingTransaction,
            times(1)
        )
            .process(
                PAYMENT_ID,
                OWNER_TOKEN
            );
    }

    private static StoredPaymentResponse
    successResponse() {
        return new StoredPaymentResponse(
            201,
            StoredPaymentResponse.APPLICATION_JSON,
            """
            {"paymentId":"10000000-0000-0000-0000-000000000001","status":"COMPLETED","ledgerTransactionId":"30000000-0000-0000-0000-000000000001"}
            """
                .strip()
        );
    }

    private static StoredPaymentResponse
    processingFailureResponse() {
        return PaymentResponseFactory.failed(
            PAYMENT_ID,
            PaymentFailureReason
                .PROCESSING_FAILED
        );
    }

    private static StoredPaymentResponse
    concurrentFailureResponse() {
        return PaymentResponseFactory.failed(
            PAYMENT_ID,
            PaymentFailureReason
                .CONCURRENT_MODIFICATION
        );
    }
}
