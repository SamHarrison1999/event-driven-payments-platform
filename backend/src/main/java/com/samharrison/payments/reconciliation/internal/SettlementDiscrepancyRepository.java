package com.samharrison.payments.reconciliation.internal;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

interface SettlementDiscrepancyRepository
    extends JpaRepository<SettlementDiscrepancy, UUID> {
}
