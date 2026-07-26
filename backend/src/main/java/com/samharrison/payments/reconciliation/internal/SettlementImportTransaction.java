package com.samharrison.payments.reconciliation.internal;

import com.samharrison.payments.audit.BusinessAuditEvents;
import com.samharrison.payments.audit.BusinessAuditRecorder;
import com.samharrison.payments.payment.PaymentReconciliationReader;
import com.samharrison.payments.payment.PaymentReconciliationSnapshot;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
class SettlementImportTransaction {

    private final SettlementImportReservationStore
        reservationStore;

    private final SettlementImportRepository importRepository;

    private final ImportedSettlementRecordRepository
        recordRepository;

    private final SettlementResultRepository
        resultRepository;

    private final SettlementDiscrepancyRepository
        discrepancyRepository;

    private final SettlementMatchClaimStore
        matchClaimStore;

    private final PaymentReconciliationReader
        paymentReader;

    private final SettlementMatcher matcher;

    private final BusinessAuditRecorder auditRecorder;

    private final Clock clock;

    SettlementImportTransaction(
        SettlementImportReservationStore
            reservationStore,
        SettlementImportRepository importRepository,
        ImportedSettlementRecordRepository
            recordRepository,
        SettlementResultRepository resultRepository,
        SettlementDiscrepancyRepository
            discrepancyRepository,
        SettlementMatchClaimStore matchClaimStore,
        PaymentReconciliationReader paymentReader,
        SettlementMatcher matcher,
        BusinessAuditRecorder auditRecorder,
        Clock clock
    ) {
        this.reservationStore =
            Objects.requireNonNull(
                reservationStore,
                "reservationStore must not be null"
            );
        this.importRepository =
            Objects.requireNonNull(
                importRepository,
                "importRepository must not be null"
            );
        this.recordRepository =
            Objects.requireNonNull(
                recordRepository,
                "recordRepository must not be null"
            );
        this.resultRepository =
            Objects.requireNonNull(
                resultRepository,
                "resultRepository must not be null"
            );
        this.discrepancyRepository =
            Objects.requireNonNull(
                discrepancyRepository,
                "discrepancyRepository must not be null"
            );
        this.matchClaimStore =
            Objects.requireNonNull(
                matchClaimStore,
                "matchClaimStore must not be null"
            );
        this.paymentReader =
            Objects.requireNonNull(
                paymentReader,
                "paymentReader must not be null"
            );
        this.matcher =
            Objects.requireNonNull(
                matcher,
                "matcher must not be null"
            );
        this.auditRecorder =
            Objects.requireNonNull(
                auditRecorder,
                "auditRecorder must not be null"
            );
        this.clock =
            Objects.requireNonNull(
                clock,
                "clock must not be null"
            );
    }

    @Transactional
    SettlementImportResponse importFile(
        ParsedSettlementFile parsedFile,
        String originalFilename,
        UUID actorIdentityUserId
    ) {
        Instant startedAt = clock.instant();

        SettlementImport candidate =
            SettlementImport.processing(
                parsedFile,
                originalFilename,
                actorIdentityUserId,
                startedAt
            );

        SettlementImportReservation reservation =
            reservationStore.reserve(candidate);

        if (reservation.existingImport()) {
            return SettlementImportResponse.completed(
                reservation.settlementImport(),
                true
            );
        }

        SettlementImport settlementImport =
            reservation.settlementImport();

        List<ImportedSettlementRecord> records =
            persistRecords(
                settlementImport,
                parsedFile.records()
            );

        Set<UUID> paymentIds =
            new LinkedHashSet<>();

        records.forEach(
            record ->
                paymentIds.add(record.paymentId())
        );

        Map<UUID, PaymentReconciliationSnapshot>
            snapshots =
                paymentReader.findAll(paymentIds);

        List<SettlementResult> results =
            new ArrayList<>(records.size());
        List<SettlementDiscrepancy> discrepancies =
            new ArrayList<>();

        for (ImportedSettlementRecord record : records) {
            PaymentReconciliationSnapshot snapshot =
                snapshots.get(record.paymentId());

            ReconciliationDecision preliminary =
                matcher.evaluate(record, snapshot);

            ReconciliationDecision decision =
                preliminary.outcome()
                    == SettlementResultOutcome.MATCHED
                        && !matchClaimStore.claim(
                            record,
                            startedAt
                        )
                    ? ReconciliationDecision.discrepancy(
                        SettlementDiscrepancyCode
                            .DUPLICATE_PAYMENT_SETTLEMENT
                    )
                    : preliminary;

            SettlementResult result =
                SettlementResult.from(
                    record,
                    decision,
                    startedAt
                );

            results.add(result);

            if (
                decision.outcome()
                    == SettlementResultOutcome
                        .DISCREPANCY
            ) {
                discrepancies.add(
                    SettlementDiscrepancy.open(
                        result,
                        startedAt
                    )
                );
            }
        }

        resultRepository.saveAll(results);
        discrepancyRepository.saveAll(discrepancies);
        discrepancyRepository.flush();

        int matchedCount =
            records.size() - discrepancies.size();

        settlementImport.complete(
            records.size(),
            matchedCount,
            discrepancies.size(),
            clock.instant()
        );

        SettlementImport completed =
            importRepository.saveAndFlush(
                settlementImport
            );

        auditRecorder.record(
            BusinessAuditEvents
                .settlementImportAccepted(
                    completed.completedAt(),
                    completed.actorIdentityUserId(),
                    completed.id(),
                    completed.rowCount(),
                    completed.matchedCount(),
                    completed.discrepancyCount()
                )
        );

        return SettlementImportResponse.completed(
            completed,
            false
        );
    }

    private List<ImportedSettlementRecord>
        persistRecords(
            SettlementImport settlementImport,
            List<ParsedSettlementRecord> parsedRecords
        ) {
        List<ImportedSettlementRecord> records =
            parsedRecords
                .stream()
                .map(
                    record ->
                        ImportedSettlementRecord.from(
                            settlementImport,
                            record
                        )
                )
                .toList();

        try {
            return recordRepository.saveAllAndFlush(
                records
            );
        } catch (
            DataIntegrityViolationException exception
        ) {
            if (
                !containsConstraint(
                    exception,
                    "uq_settlement_record_external_id"
                )
            ) {
                throw exception;
            }

            throw new SettlementImportConflictException(
                "A settlement record identifier was "
                    + "already accepted by another import.",
                exception
            );
        }
    }

    private static boolean containsConstraint(
        Throwable failure,
        String constraintName
    ) {
        Throwable current = failure;

        while (current != null) {
            if (
                current.getMessage() != null
                    && current
                        .getMessage()
                        .contains(constraintName)
            ) {
                return true;
            }

            current = current.getCause();
        }

        return false;
    }
}
