package com.samharrison.payments.payment;

import java.util.List;

public interface PaymentOperationalReportReader {

    PaymentOperationalSummary summarize(
        PaymentReportQuery query
    );

    List<PaymentReportRow> readRows(
        PaymentReportQuery query
    );
}
