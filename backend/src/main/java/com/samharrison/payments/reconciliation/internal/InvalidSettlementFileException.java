package com.samharrison.payments.reconciliation.internal;

final class InvalidSettlementFileException
    extends RuntimeException {

    private static final long serialVersionUID = 1L;

    private final SettlementFileErrorCode code;
    private final Integer rowNumber;

    InvalidSettlementFileException(
        SettlementFileErrorCode code,
        String message
    ) {
        this(code, null, message, null);
    }

    InvalidSettlementFileException(
        SettlementFileErrorCode code,
        int rowNumber,
        String message
    ) {
        this(code, rowNumber, message, null);
    }

    InvalidSettlementFileException(
        SettlementFileErrorCode code,
        String message,
        Throwable cause
    ) {
        this(code, null, message, cause);
    }

    private InvalidSettlementFileException(
        SettlementFileErrorCode code,
        Integer rowNumber,
        String message,
        Throwable cause
    ) {
        super(message, cause);
        this.code =
            java.util.Objects.requireNonNull(
                code,
                "code must not be null"
            );
        this.rowNumber = rowNumber;
    }

    SettlementFileErrorCode code() {
        return code;
    }

    Integer rowNumber() {
        return rowNumber;
    }
}
