package com.samharrison.payments.ledger.internal;

import java.util.List;
import java.util.Objects;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
class LedgerPersistenceStore {

    private final LedgerTransactionRecordRepository
        transactionRepository;

    private final LedgerEntryRecordRepository
        entryRepository;

    LedgerPersistenceStore(
        LedgerTransactionRecordRepository
            transactionRepository,
        LedgerEntryRecordRepository
            entryRepository
    ) {
        this.transactionRepository =
            transactionRepository;
        this.entryRepository = entryRepository;
    }

    @Transactional
    void save(
        LedgerTransaction transaction
    ) {
        LedgerTransaction required =
            Objects.requireNonNull(
                transaction,
                "transaction must not be null"
            );

        transactionRepository.saveAndFlush(
            LedgerTransactionRecord.from(required)
        );

        List<LedgerEntryRecord> entries =
            required
                .entries()
                .stream()
                .map(LedgerEntryRecord::from)
                .toList();

        entryRepository.saveAllAndFlush(entries);
    }
}