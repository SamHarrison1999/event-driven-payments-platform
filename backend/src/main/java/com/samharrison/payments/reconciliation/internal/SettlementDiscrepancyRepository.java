package com.samharrison.payments.reconciliation.internal;

import jakarta.persistence.LockModeType;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

interface SettlementDiscrepancyRepository
    extends JpaRepository<SettlementDiscrepancy, UUID> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query(
        """
        SELECT discrepancy
        FROM SettlementDiscrepancy discrepancy
        WHERE discrepancy.id = :discrepancyId
        """
    )
    Optional<SettlementDiscrepancy> findForUpdate(
        @Param("discrepancyId")
        UUID discrepancyId
    );
}
