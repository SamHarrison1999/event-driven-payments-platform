package com.samharrison.payments.reconciliation;

import java.util.List;

public interface OperationalReconciliationReportReader {

    SettlementOperationalSummary summarizeSettlements(
        ReconciliationReportQuery query
    );

    ReconciliationOperationalSummary
        summarizeReconciliation(
            ReconciliationReportQuery query
        );

    List<SettlementReportRow> readSettlementRows(
        ReconciliationReportQuery query
    );

    List<ReconciliationReportRow>
        readReconciliationRows(
            ReconciliationReportQuery query
        );
}
