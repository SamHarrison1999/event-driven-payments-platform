package com.samharrison.payments.reconciliation.internal;

import java.util.Objects;

record ReconciliationDecision(
    SettlementResultOutcome outcome,
    SettlementDiscrepancyCode discrepancyCode
) {

    ReconciliationDecision {
        Objects.requireNonNull(
            outcome,
            "outcome must not be null"
        );

        if (
            outcome == SettlementResultOutcome.MATCHED
                && discrepancyCode != null
        ) {
            throw new IllegalArgumentException(
                "A matched decision cannot have "
                    + "a discrepancy code."
            );
        }

        if (
            outcome
                == SettlementResultOutcome.DISCREPANCY
                && discrepancyCode == null
        ) {
            throw new IllegalArgumentException(
                "A discrepancy decision requires a code."
            );
        }
    }

    static ReconciliationDecision matched() {
        return new ReconciliationDecision(
            SettlementResultOutcome.MATCHED,
            null
        );
    }

    static ReconciliationDecision discrepancy(
        SettlementDiscrepancyCode code
    ) {
        return new ReconciliationDecision(
            SettlementResultOutcome.DISCREPANCY,
            code
        );
    }
}
