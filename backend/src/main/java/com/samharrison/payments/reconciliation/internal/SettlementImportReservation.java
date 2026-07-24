package com.samharrison.payments.reconciliation.internal;

import java.util.Objects;

record SettlementImportReservation(
    SettlementImport settlementImport,
    boolean existingImport
) {

    SettlementImportReservation {
        Objects.requireNonNull(
            settlementImport,
            "settlementImport must not be null"
        );
    }
}
