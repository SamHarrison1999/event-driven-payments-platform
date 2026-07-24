package com.samharrison.payments.reconciliation.internal;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

interface SettlementResolutionRepository
    extends JpaRepository<SettlementResolution, UUID> {
}
