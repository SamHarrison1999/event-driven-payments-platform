package com.samharrison.payments.reconciliation.internal;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

interface SettlementImportRepository
    extends JpaRepository<SettlementImport, UUID> {

    Optional<SettlementImport> findByRawFileSha256(
        String rawFileSha256
    );
}
