package com.samharrison.payments.payment.internal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.UUID;
import io.micrometer.observation.ObservationRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;

@ExtendWith(MockitoExtension.class)
class PaymentSubmissionServiceTest {

    private static final UUID ACTOR_ID =
        UUID.fromString(
            "10000000-0000-0000-0000-000000000001"
        );

    private static final UUID PAYMENT_ID =
        UUID.fromString(
            "20000000-0000-0000-0000-000000000001"
        );

    private static final UUID OWNER_TOKEN =
        UUID.fromString(
            "30000000-0000-0000-0000-000000000001"
        );

    private static final UUID SOURCE_ACCOUNT_ID =
        UUID.fromString(
            "40000000-0000-0000-0000-000000000001"
        );

    private static final UUID DESTINATION_ACCOUNT_ID =
        UUID.fromString(
            "50000000-0000-0000-0000-000000000001"
        );

    @Mock
    private PaymentReservationCoordinator
        reservationCoordinator;

    @Mock
    private PaymentProcessingCoordinator
        processingCoordinator;

    private PaymentSubmissionService service;

    private PaymentMetrics metrics;

    @BeforeEach
    void setUp() {
        metrics = new PaymentMetrics(
            new SimpleMeterRegistry(),
            ObservationRegistry.create()
        );

        service =
            new PaymentSubmissionService(
                reservationCoordinator,
                processingCoordinator,
                metrics
            );
    }

    @Test
    void processesNewReservation() {
        PaymentCreateRequest request = request();
        PaymentRequestData domainRequest =
            request.toDomain();

        StoredPaymentResponse response =
            completedResponse();

        when(
            reservationCoordinator.reserve(
                ACTOR_ID,
                IdempotencyKey.of("submit-1001"),
                domainRequest
            )
        )
            .thenReturn(
                new PaymentReservationResult.Acquired(
                    PAYMENT_ID,
                    OWNER_TOKEN
                )
            );

        when(
            processingCoordinator.process(
                PAYMENT_ID,
                OWNER_TOKEN
            )
        )
            .thenReturn(response);

        assertThat(
            service.submit(
                ACTOR_ID,
                "submit-1001",
                request
            )
        )
            .isSameAs(response);
    }

    @Test
    void returnsStoredReplayWithoutProcessing() {
        PaymentCreateRequest request = request();

        StoredPaymentResponse response =
            completedResponse();

        when(
            reservationCoordinator.reserve(
                ACTOR_ID,
                IdempotencyKey.of("submit-replay"),
                request.toDomain()
            )
        )
            .thenReturn(
                new PaymentReservationResult.Replay(
                    response
                )
            );

        assertThat(
            service.submit(
                ACTOR_ID,
                "submit-replay",
                request
            )
        )
            .isSameAs(response);

        verifyNoInteractions(processingCoordinator);
    }

    @Test
    void reportsReusedIdempotencyKey() {
        PaymentCreateRequest request = request();

        when(
            reservationCoordinator.reserve(
                ACTOR_ID,
                IdempotencyKey.of("submit-conflict"),
                request.toDomain()
            )
        )
            .thenReturn(
                new PaymentReservationResult.Conflict(
                    PaymentReservationResult
                        .Reason
                        .IDEMPOTENCY_KEY_REUSED
                )
            );

        assertThatThrownBy(
            () ->
                service.submit(
                    ACTOR_ID,
                    "submit-conflict",
                    request
                )
        )
            .isInstanceOfSatisfying(
                PaymentIdempotencyConflictException.class,
                exception ->
                    assertThat(exception.reason())
                        .isEqualTo(
                            PaymentReservationResult
                                .Reason
                                .IDEMPOTENCY_KEY_REUSED
                        )
            );

        verifyNoInteractions(processingCoordinator);
    }

    @Test
    void validatesDomainRequestBeforeReservation() {
        PaymentCreateRequest request =
            new PaymentCreateRequest(
                SOURCE_ACCOUNT_ID,
                SOURCE_ACCOUNT_ID,
                100L
            );

        assertThatThrownBy(
            () ->
                service.submit(
                    ACTOR_ID,
                    "submit-invalid",
                    request
                )
        )
            .isInstanceOf(
                InvalidPaymentException.class
            );

        verifyNoInteractions(
            reservationCoordinator,
            processingCoordinator
        );
    }

    @Test
    void requiresIdempotencyKeyBeforeReservation() {
        PaymentCreateRequest request = request();

        assertThatThrownBy(
            () ->
                service.submit(
                    ACTOR_ID,
                    null,
                    request
                )
        )
            .isInstanceOf(
                PaymentIdempotencyKeyRequiredException.class
            );

        verifyNoInteractions(
            reservationCoordinator,
            processingCoordinator
        );
    }

    private static PaymentCreateRequest request() {
        return new PaymentCreateRequest(
            SOURCE_ACCOUNT_ID,
            DESTINATION_ACCOUNT_ID,
            250L
        );
    }

    private static StoredPaymentResponse
    completedResponse() {
        return PaymentResponseFactory.completed(
            PAYMENT_ID,
            UUID.fromString(
                "60000000-0000-0000-0000-000000000001"
            )
        );
    }
}
