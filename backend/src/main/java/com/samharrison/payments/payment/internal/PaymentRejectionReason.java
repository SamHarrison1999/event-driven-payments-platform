package com.samharrison.payments.payment.internal;

public enum PaymentRejectionReason {
    SOURCE_NOT_OWNED(
        "PAYMENT_SOURCE_NOT_OWNED"
    ),
    SOURCE_NOT_FOUND(
        "PAYMENT_SOURCE_NOT_FOUND"
    ),
    DESTINATION_NOT_FOUND(
        "PAYMENT_DESTINATION_NOT_FOUND"
    ),
    SOURCE_NOT_ACTIVE(
        "PAYMENT_SOURCE_NOT_ACTIVE"
    ),
    DESTINATION_NOT_ACTIVE(
        "PAYMENT_DESTINATION_NOT_ACTIVE"
    ),
    CURRENCY_MISMATCH(
        "PAYMENT_CURRENCY_MISMATCH"
    ),
    INSUFFICIENT_FUNDS(
        "PAYMENT_INSUFFICIENT_FUNDS"
    );

    private final String code;

    PaymentRejectionReason(
        String code
    ) {
        this.code = code;
    }

    public String code() {
        return code;
    }
}
