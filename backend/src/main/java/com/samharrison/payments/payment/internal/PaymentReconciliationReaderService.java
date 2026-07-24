package com.samharrison.payments.payment.internal;

import com.samharrison.payments.payment.PaymentReconciliationReader;
import com.samharrison.payments.payment.PaymentReconciliationSnapshot;
import com.samharrison.payments.payment.PaymentReconciliationStatus;
import com.samharrison.payments.shared.GbpAmount;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
class PaymentReconciliationReaderService
    implements PaymentReconciliationReader {

    private final PaymentRepository repository;

    PaymentReconciliationReaderService(
        PaymentRepository repository
    ) {
        this.repository =
            Objects.requireNonNull(
                repository,
                "repository must not be null"
            );
    }

    @Override
    @Transactional(readOnly = true)
    @PreAuthorize(
        "hasAnyRole('RECONCILIATION_ANALYST', 'ADMIN')"
    )
    public Map<UUID, PaymentReconciliationSnapshot>
        findAll(
            Set<UUID> paymentIds
        ) {
        Set<UUID> requiredIds =
            Set.copyOf(
                Objects.requireNonNull(
                    paymentIds,
                    "paymentIds must not be null"
                )
            );

        if (
            requiredIds.size()
                > MAX_PAYMENT_IDS
        ) {
            throw new IllegalArgumentException(
                "At most 1,000 payment identifiers "
                    + "may be read at once."
            );
        }

        if (requiredIds.isEmpty()) {
            return Map.of();
        }

        Map<UUID, PaymentReconciliationSnapshot>
            snapshots = new LinkedHashMap<>();

        repository
            .findAllById(requiredIds)
            .forEach(
                payment ->
                    snapshots.put(
                        payment.id(),
                        toSnapshot(payment)
                    )
            );

        return Map.copyOf(snapshots);
    }

    private static PaymentReconciliationSnapshot
        toSnapshot(
            Payment payment
        ) {
        boolean completed =
            payment.status() == PaymentStatus.COMPLETED;

        return new PaymentReconciliationSnapshot(
            payment.id(),
            PaymentReconciliationStatus.valueOf(
                payment.status().name()
            ),
            payment.request().amount().minorUnits(),
            GbpAmount.CURRENCY_CODE,
            completed
                ? payment.updatedAt()
                : null,
            completed
                ? payment.ledgerTransactionId()
                : null
        );
    }
}
