package com.samharrison.payments.reconciliation.internal;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public record SettlementImportResponse(
    UUID importId,
    boolean existingImport,
    String status,
    String originalFilename,
    String rawFileSha256,
    int rawFileSizeBytes,
    int rowCount,
    int matchedCount,
    int discrepancyCount,
    Instant createdAt,
    Instant completedAt
) {

    static SettlementImportResponse completed(
        SettlementImport settlementImport,
        boolean existingImport
    ) {
        SettlementImport requiredImport =
            Objects.requireNonNull(
                settlementImport,
                "settlementImport must not be null"
            );

        if (
            requiredImport.status()
                != SettlementImportStatus.COMPLETED
        ) {
            throw new IllegalArgumentException(
                "Only a completed settlement import "
                    + "may be exposed."
            );
        }

        return new SettlementImportResponse(
            requiredImport.id(),
            existingImport,
            requiredImport.status().name(),
            requiredImport.originalFilename(),
            requiredImport.rawFileSha256(),
            requiredImport.rawFileSizeBytes(),
            requiredImport.rowCount(),
            requiredImport.matchedCount(),
            requiredImport.discrepancyCount(),
            requiredImport.createdAt(),
            requiredImport.completedAt()
        );
    }
}
