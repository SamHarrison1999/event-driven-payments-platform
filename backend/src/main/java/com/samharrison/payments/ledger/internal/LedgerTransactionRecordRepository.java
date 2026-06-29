package com.samharrison.payments.ledger.internal;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

interface LedgerTransactionRecordRepository
    extends JpaRepository<LedgerTransactionRecord, UUID> {
}