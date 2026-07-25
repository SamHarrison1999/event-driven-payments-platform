package com.samharrison.payments.reporting.internal;

import com.samharrison.payments.payment.PaymentOperationalReportReader;
import com.samharrison.payments.payment.PaymentReportQuery;
import com.samharrison.payments.payment.PaymentReportRow;
import com.samharrison.payments.reconciliation.OperationalReconciliationReportReader;
import com.samharrison.payments.reconciliation.ReconciliationReportQuery;
import com.samharrison.payments.reconciliation.ReconciliationReportRow;
import com.samharrison.payments.reconciliation.SettlementReportRow;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Function;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

@Service
class ReportExportService {

    static final int MAXIMUM_ROWS = 10_000;

    private static final int OVERFLOW_LIMIT =
        MAXIMUM_ROWS + 1;

    private final AuditSearchService auditSearchService;

    private final PaymentOperationalReportReader
        paymentReader;

    private final OperationalReconciliationReportReader
        reconciliationReader;

    ReportExportService(
        AuditSearchService auditSearchService,
        PaymentOperationalReportReader paymentReader,
        OperationalReconciliationReportReader
            reconciliationReader
    ) {
        this.auditSearchService =
            Objects.requireNonNull(
                auditSearchService,
                "auditSearchService must not be null"
            );
        this.paymentReader =
            Objects.requireNonNull(
                paymentReader,
                "paymentReader must not be null"
            );
        this.reconciliationReader =
            Objects.requireNonNull(
                reconciliationReader,
                "reconciliationReader must not be null"
            );
    }

    @Transactional(
        readOnly = true,
        isolation = Isolation.REPEATABLE_READ
    )
    @PreAuthorize(
        "hasAnyRole('OPERATIONS', "
            + "'RECONCILIATION_ANALYST', 'ADMIN')"
    )
    CsvReport auditEvents(ReportWindow window) {
        List<AuditEventResponse> events =
            auditSearchService.export(
                window.from(),
                window.to(),
                OVERFLOW_LIMIT
            );
        requireWithinLimit(events);

        List<List<String>> rows =
            events
                .stream()
                .map(ReportExportService::auditRow)
                .toList();

        return report(
            "audit-events.csv",
            List.of(
                "event_id",
                "source",
                "category",
                "event_type",
                "schema_version",
                "occurred_at",
                "actor_kind",
                "actor_identity_user_id",
                "subject_type",
                "subject_identifier",
                "correlation_identifier"
            ),
            rows
        );
    }

    @Transactional(
        readOnly = true,
        isolation = Isolation.REPEATABLE_READ
    )
    @PreAuthorize(
        "hasAnyRole('OPERATIONS', 'ADMIN')"
    )
    CsvReport payments(ReportWindow window) {
        List<PaymentReportRow> paymentRows =
            paymentReader.readRows(
                new PaymentReportQuery(
                    window.from(),
                    window.to(),
                    OVERFLOW_LIMIT
                )
            );
        requireWithinLimit(paymentRows);

        return report(
            "payments.csv",
            List.of(
                "payment_id",
                "actor_identity_user_id",
                "source_account_id",
                "destination_account_id",
                "amount_minor_units",
                "currency",
                "status",
                "ledger_transaction_id",
                "rejection_code",
                "failure_code",
                "created_at",
                "updated_at"
            ),
            mapRows(
                paymentRows,
                ReportExportService::paymentRow
            )
        );
    }

    @Transactional(
        readOnly = true,
        isolation = Isolation.REPEATABLE_READ
    )
    @PreAuthorize(
        "hasAnyRole('RECONCILIATION_ANALYST', "
            + "'ADMIN')"
    )
    CsvReport settlements(ReportWindow window) {
        List<SettlementReportRow> settlementRows =
            reconciliationReader
                .readSettlementRows(
                    reconciliationQuery(window)
                );
        requireWithinLimit(settlementRows);

        return report(
            "settlements.csv",
            List.of(
                "settlement_import_id",
                "row_number",
                "settlement_record_id",
                "payment_id",
                "amount_minor_units",
                "currency",
                "settled_at",
                "outcome",
                "discrepancy_code",
                "import_completed_at"
            ),
            mapRows(
                settlementRows,
                ReportExportService::settlementRow
            )
        );
    }

    @Transactional(
        readOnly = true,
        isolation = Isolation.REPEATABLE_READ
    )
    @PreAuthorize(
        "hasAnyRole('RECONCILIATION_ANALYST', "
            + "'ADMIN')"
    )
    CsvReport reconciliation(
        ReportWindow window
    ) {
        List<ReconciliationReportRow>
            reconciliationRows =
                reconciliationReader
                    .readReconciliationRows(
                        reconciliationQuery(window)
                    );
        requireWithinLimit(reconciliationRows);

        return report(
            "reconciliation.csv",
            List.of(
                "discrepancy_id",
                "settlement_import_id",
                "settlement_record_id",
                "code",
                "status",
                "created_at",
                "resolution_decision",
                "resolved_at",
                "resolution_actor_identity_user_id"
            ),
            mapRows(
                reconciliationRows,
                ReportExportService
                    ::reconciliationRow
            )
        );
    }

    private static ReconciliationReportQuery
        reconciliationQuery(
            ReportWindow window
        ) {
        return new ReconciliationReportQuery(
            window.from(),
            window.to(),
            OVERFLOW_LIMIT
        );
    }

    private static List<String> auditRow(
        AuditEventResponse event
    ) {
        return List.of(
            event.eventId(),
            event.source().name(),
            event.category().name(),
            event.eventType(),
            Integer.toString(event.schemaVersion()),
            event.occurredAt().toString(),
            event.actorKind(),
            value(event.actorIdentityUserId()),
            event.subjectType(),
            event.subjectIdentifier(),
            value(event.correlationIdentifier())
        );
    }

    private static List<String> paymentRow(
        PaymentReportRow row
    ) {
        return List.of(
            row.paymentId().toString(),
            row.actorIdentityUserId().toString(),
            row.sourceAccountId().toString(),
            row.destinationAccountId().toString(),
            Long.toString(row.amountMinorUnits()),
            row.currency(),
            row.status(),
            value(row.ledgerTransactionId()),
            value(row.rejectionCode()),
            value(row.failureCode()),
            row.createdAt().toString(),
            row.updatedAt().toString()
        );
    }

    private static List<String> settlementRow(
        SettlementReportRow row
    ) {
        return List.of(
            row.settlementImportId().toString(),
            Integer.toString(row.rowNumber()),
            row.settlementRecordId(),
            row.paymentId().toString(),
            Long.toString(row.amountMinorUnits()),
            row.currency(),
            row.settledAt().toString(),
            row.outcome(),
            value(row.discrepancyCode()),
            row.importCompletedAt().toString()
        );
    }

    private static List<String> reconciliationRow(
        ReconciliationReportRow row
    ) {
        return List.of(
            row.discrepancyId().toString(),
            row.settlementImportId().toString(),
            row.settlementRecordId(),
            row.code(),
            row.status(),
            row.createdAt().toString(),
            value(row.resolutionDecision()),
            value(row.resolvedAt()),
            value(
                row.resolutionActorIdentityUserId()
            )
        );
    }

    private static <T> List<List<String>> mapRows(
        List<T> rows,
        Function<T, List<String>> mapper
    ) {
        List<List<String>> mapped =
            new ArrayList<>(rows.size());

        for (T row : rows) {
            mapped.add(mapper.apply(row));
        }

        return List.copyOf(mapped);
    }

    private static CsvReport report(
        String filename,
        List<String> header,
        List<List<String>> rows
    ) {
        return new CsvReport(
            filename,
            CsvDocumentWriter.write(header, rows)
        );
    }

    private static void requireWithinLimit(
        List<?> rows
    ) {
        if (rows.size() > MAXIMUM_ROWS) {
            throw new ReportExportTooLargeException();
        }
    }

    private static String value(Object value) {
        return value == null
            ? ""
            : value.toString();
    }

    private static String value(UUID value) {
        return value == null
            ? ""
            : value.toString();
    }
}
