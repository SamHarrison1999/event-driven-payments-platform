package com.samharrison.payments.ledger.internal;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

interface LedgerEntryRecordRepository
    extends JpaRepository<LedgerEntryRecord, UUID> {

    List<LedgerEntryRecord>
    findAllByTransactionIdOrderBySequenceAsc(
        UUID transactionId
    );
}