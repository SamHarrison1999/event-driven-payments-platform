package com.samharrison.payments.reporting.internal;

final class ReportExportTooLargeException
    extends RuntimeException {

    private static final long serialVersionUID = 1L;

    ReportExportTooLargeException() {
        super(
            "The report contains more than 10000 "
                + "data rows."
        );
    }
}
