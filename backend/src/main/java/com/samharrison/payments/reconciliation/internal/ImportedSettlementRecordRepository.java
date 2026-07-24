package com.samharrison.payments.reconciliation.internal;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

interface ImportedSettlementRecordRepository
    extends
        JpaRepository<ImportedSettlementRecord, UUID> {

    List<ImportedSettlementRecord>
        findAllBySettlementImportIdOrderByRowNumber(
            UUID settlementImportId
        );
}
