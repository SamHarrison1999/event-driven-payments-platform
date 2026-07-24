package com.samharrison.payments.reconciliation.internal;

import com.samharrison.payments.payment.PaymentReconciliationSnapshot;
import com.samharrison.payments.payment.PaymentReconciliationStatus;
import java.util.Objects;
import org.springframework.stereotype.Component;

@Component
class SettlementMatcher {

    ReconciliationDecision evaluate(
        ImportedSettlementRecord record,
        PaymentReconciliationSnapshot payment
    ) {
        ImportedSettlementRecord requiredRecord =
            Objects.requireNonNull(
                record,
                "record must not be null"
            );

        if (payment == null) {
            return discrepancy(
                SettlementDiscrepancyCode.PAYMENT_NOT_FOUND
            );
        }

        if (
            payment.status()
                != PaymentReconciliationStatus.COMPLETED
        ) {
            return discrepancy(
                SettlementDiscrepancyCode
                    .PAYMENT_NOT_COMPLETED
            );
        }

        if (
            !requiredRecord
                .currency()
                .equals(payment.currency())
        ) {
            return discrepancy(
                SettlementDiscrepancyCode
                    .CURRENCY_MISMATCH
            );
        }

        if (
            requiredRecord.amountMinorUnits()
                != payment.amountMinorUnits()
        ) {
            return discrepancy(
                SettlementDiscrepancyCode
                    .AMOUNT_MISMATCH
            );
        }

        if (
            requiredRecord
                .settledAt()
                .isBefore(payment.completedAt())
        ) {
            return discrepancy(
                SettlementDiscrepancyCode
                    .SETTLED_BEFORE_COMPLETION
            );
        }

        return ReconciliationDecision.matched();
    }

    private static ReconciliationDecision discrepancy(
        SettlementDiscrepancyCode code
    ) {
        return ReconciliationDecision.discrepancy(code);
    }
}
