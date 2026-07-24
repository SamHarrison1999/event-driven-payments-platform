package com.samharrison.payments.reconciliation.internal;

import java.util.List;
import java.util.Objects;

record ParsedSettlementFile(
    String rawFileSha256,
    int rawFileSizeBytes,
    List<ParsedSettlementRecord> records
) {

    ParsedSettlementFile {
        Objects.requireNonNull(
            rawFileSha256,
            "rawFileSha256 must not be null"
        );

        if (rawFileSizeBytes <= 0) {
            throw new IllegalArgumentException(
                "rawFileSizeBytes must be positive"
            );
        }

        records = List.copyOf(records);

        if (records.isEmpty()) {
            throw new IllegalArgumentException(
                "records must not be empty"
            );
        }
    }
}
