package com.samharrison.payments.reporting.internal;

import com.samharrison.payments.payment.PaymentOperationalReportReader;
import com.samharrison.payments.payment.PaymentOperationalSummary;
import com.samharrison.payments.payment.PaymentReportQuery;
import com.samharrison.payments.reconciliation.OperationalReconciliationReportReader;
import com.samharrison.payments.reconciliation.ReconciliationOperationalSummary;
import com.samharrison.payments.reconciliation.ReconciliationReportQuery;
import com.samharrison.payments.reconciliation.SettlementOperationalSummary;
import java.util.Objects;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

@Service
class OperationalSummaryService {

    private final PaymentOperationalReportReader
        paymentReader;

    private final OperationalReconciliationReportReader
        reconciliationReader;

    OperationalSummaryService(
        PaymentOperationalReportReader paymentReader,
        OperationalReconciliationReportReader
            reconciliationReader
    ) {
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
    OperationalSummaryResponse summarize(
        ReportWindow window
    ) {
        ReportWindow requiredWindow =
            Objects.requireNonNull(
                window,
                "window must not be null"
            );
        ReportingAuthorityScope scope =
            ReportingAuthorityScope.current();

        PaymentOperationalSummary payment = null;
        SettlementOperationalSummary settlement =
            null;
        ReconciliationOperationalSummary
            reconciliation = null;

        if (scope.mayReadPayments()) {
            payment =
                paymentReader.summarize(
                    new PaymentReportQuery(
                        requiredWindow.from(),
                        requiredWindow.to(),
                        1
                    )
                );
        }

        if (scope.mayReadReconciliation()) {
            ReconciliationReportQuery query =
                new ReconciliationReportQuery(
                    requiredWindow.from(),
                    requiredWindow.to(),
                    1
                );

            settlement =
                reconciliationReader
                    .summarizeSettlements(query);
            reconciliation =
                reconciliationReader
                    .summarizeReconciliation(query);
        }

        return new OperationalSummaryResponse(
            requiredWindow.from(),
            requiredWindow.to(),
            payment,
            settlement,
            reconciliation
        );
    }
}
