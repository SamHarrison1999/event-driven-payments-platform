package com.samharrison.payments.payment.internal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.samharrison.payments.payment.PaymentReconciliationSnapshot;
import com.samharrison.payments.payment.PaymentReconciliationStatus;
import com.samharrison.payments.shared.GbpAmount;
import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.IntStream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class PaymentReconciliationReaderServiceTest {

    private static final UUID ACTOR_ID =
        UUID.fromString(
            "10000000-0000-0000-0000-000000000001"
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
        Instant.parse("2026-07-24T10:00:00Z");

    @Mock
    private PaymentRepository repository;

    private PaymentReconciliationReaderService service;

    @BeforeEach
    void setUp() {
        service =
            new PaymentReconciliationReaderService(
                repository
            );
    }

    @Test
    void returnsOnlyFoundPaymentsWithCompletionEvidence() {
        Payment completed = payment(250L);
        UUID ledgerTransactionId = UUID.randomUUID();
        Instant completedAt =
            CREATED_AT.plusSeconds(5L);

        completed.startProcessing(
            CREATED_AT.plusSeconds(1L)
        );
        completed.complete(
            ledgerTransactionId,
            completedAt
        );

        Payment pending = payment(500L);
        Set<UUID> requested =
            Set.of(
                completed.id(),
                pending.id(),
                UUID.randomUUID()
            );

        when(repository.findAllById(requested))
            .thenReturn(
                List.of(completed, pending)
            );

        Map<UUID, PaymentReconciliationSnapshot>
            snapshots = service.findAll(requested);

        assertThat(snapshots)
            .hasSize(2);

        assertThat(snapshots.get(completed.id()))
            .isEqualTo(
                new PaymentReconciliationSnapshot(
                    completed.id(),
                    PaymentReconciliationStatus
                        .COMPLETED,
                    250L,
                    "GBP",
                    completedAt,
                    ledgerTransactionId
                )
            );

        PaymentReconciliationSnapshot pendingSnapshot =
            snapshots.get(pending.id());

        assertThat(pendingSnapshot.status())
            .isEqualTo(
                PaymentReconciliationStatus.PENDING
            );
        assertThat(pendingSnapshot.completedAt())
            .isNull();
        assertThat(
            pendingSnapshot.ledgerTransactionId()
        )
            .isNull();
    }

    @Test
    void emptyRequestDoesNotQueryTheRepository() {
        assertThat(service.findAll(Set.of()))
            .isEmpty();

        verifyNoInteractions(repository);
    }

    @Test
    void rejectsMoreThanOneThousandIdentifiers() {
        Set<UUID> identifiers =
            IntStream
                .rangeClosed(1, 1_001)
                .mapToObj(
                    ignored -> UUID.randomUUID()
                )
                .collect(
                    java.util.stream.Collectors
                        .toCollection(
                            LinkedHashSet::new
                        )
                );

        assertThatThrownBy(
            () -> service.findAll(identifiers)
        )
            .isInstanceOf(
                IllegalArgumentException.class
            )
            .hasMessageContaining("1,000");

        verifyNoInteractions(repository);
    }

    private static Payment payment(
        long amountMinorUnits
    ) {
        return Payment.pending(
            ACTOR_ID,
            new PaymentRequestData(
                SOURCE_ACCOUNT_ID,
                DESTINATION_ACCOUNT_ID,
                GbpAmount.ofMinorUnits(
                    amountMinorUnits
                )
            ),
            CREATED_AT
        );
    }
}
