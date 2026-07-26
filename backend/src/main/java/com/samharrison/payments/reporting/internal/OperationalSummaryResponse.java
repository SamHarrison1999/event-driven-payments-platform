package com.samharrison.payments.reporting.internal;

import com.samharrison.payments.payment.PaymentOperationalSummary;
import com.samharrison.payments.reconciliation.ReconciliationOperationalSummary;
import com.samharrison.payments.reconciliation.SettlementOperationalSummary;
import java.time.Instant;

record OperationalSummaryResponse(
    Instant from,
    Instant to,
    PaymentOperationalSummary payment,
    SettlementOperationalSummary settlement,
    ReconciliationOperationalSummary reconciliation
) {
}
