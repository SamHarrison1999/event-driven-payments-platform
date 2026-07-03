package com.samharrison.payments.payment.internal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import com.samharrison.payments.shared.GbpAmount;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;

@ExtendWith(MockitoExtension.class)
class PaymentQueryServiceTest {

    private static final UUID ACTOR_ID =
        UUID.fromString(
            "10000000-0000-0000-0000-000000000001"
        );

    private static final UUID OTHER_ACTOR_ID =
        UUID.fromString(
            "10000000-0000-0000-0000-000000000002"
        );

    private static final UUID SOURCE_ACCOUNT_ID =
        UUID.fromString(
            "20000000-0000-0000-0000-000000000001"
        );

    private static final UUID DESTINATION_ACCOUNT_ID =
        UUID.fromString(
            "30000000-0000-0000-0000-000000000001"
        );

    private static final Instant CREATED_AT =
        Instant.parse(
            "2026-07-03T12:00:00Z"
        );

    @Mock
    private PaymentRepository repository;

    private PaymentQueryService service;

    @BeforeEach
    void setUp() {
        service =
            new PaymentQueryService(repository);
    }

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void customerReadsOwnPayment() {
        Payment payment = payment(ACTOR_ID);

        when(repository.findById(payment.id()))
            .thenReturn(Optional.of(payment));

        authenticate("CUSTOMER");

        PaymentResponse response =
            service.find(
                ACTOR_ID,
                payment.id()
            );

        assertThat(response.paymentId())
            .isEqualTo(payment.id());

        assertThat(response.sourceAccountId())
            .isEqualTo(SOURCE_ACCOUNT_ID);

        assertThat(response.destinationAccountId())
            .isEqualTo(DESTINATION_ACCOUNT_ID);

        assertThat(response.amountMinorUnits())
            .isEqualTo(250L);

        assertThat(response.currency())
            .isEqualTo("GBP");

        assertThat(response.status())
            .isEqualTo("PENDING");

        assertThat(response.createdAt())
            .isEqualTo(CREATED_AT);

        assertThat(response.version())
            .isZero();
    }

    @Test
    void customerCannotDiscoverAnotherPayment() {
        Payment payment = payment(OTHER_ACTOR_ID);

        when(repository.findById(payment.id()))
            .thenReturn(Optional.of(payment));

        authenticate("CUSTOMER");

        assertThatThrownBy(
            () ->
                service.find(
                    ACTOR_ID,
                    payment.id()
                )
        )
            .isInstanceOf(
                PaymentNotFoundException.class
            )
            .hasMessageContaining(
                payment.id().toString()
            );
    }

    @Test
    void operationsUserReadsAnyPayment() {
        Payment payment = payment(OTHER_ACTOR_ID);

        when(repository.findById(payment.id()))
            .thenReturn(Optional.of(payment));

        authenticate("OPERATIONS");

        assertThat(
            service.find(
                ACTOR_ID,
                payment.id()
            )
                .paymentId()
        )
            .isEqualTo(payment.id());
    }

    @Test
    void missingPaymentIsReported() {
        UUID missingPaymentId =
            UUID.randomUUID();

        when(repository.findById(missingPaymentId))
            .thenReturn(Optional.empty());

        authenticate("ADMIN");

        assertThatThrownBy(
            () ->
                service.find(
                    ACTOR_ID,
                    missingPaymentId
                )
        )
            .isInstanceOf(
                PaymentNotFoundException.class
            );
    }

    @Test
    void terminalReasonsUseStableCodes() {
        Payment payment = payment(ACTOR_ID);

        payment.startProcessing(
            CREATED_AT.plusSeconds(1L)
        );

        payment.reject(
            PaymentRejectionReason
                .INSUFFICIENT_FUNDS,
            CREATED_AT.plusSeconds(2L)
        );

        PaymentResponse response =
            PaymentResponse.from(payment);

        assertThat(response.status())
            .isEqualTo("REJECTED");

        assertThat(response.rejectionReason())
            .isEqualTo(
                "PAYMENT_INSUFFICIENT_FUNDS"
            );

        assertThat(response.failureReason())
            .isNull();

        assertThat(response.ledgerTransactionId())
            .isNull();
    }

    private static Payment payment(
        UUID actorId
    ) {
        return Payment.pending(
            actorId,
            new PaymentRequestData(
                SOURCE_ACCOUNT_ID,
                DESTINATION_ACCOUNT_ID,
                GbpAmount.ofMinorUnits(250L)
            ),
            CREATED_AT
        );
    }

    private static void authenticate(
        String role
    ) {
        var authentication =
            new UsernamePasswordAuthenticationToken(
                "query-test-user",
                "not-used",
                List.of(
                    new SimpleGrantedAuthority(
                        "ROLE_" + role
                    )
                )
            );

        SecurityContext context =
            SecurityContextHolder
                .createEmptyContext();

        context.setAuthentication(authentication);

        SecurityContextHolder.setContext(context);
    }
}
