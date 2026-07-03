package com.samharrison.payments.payment.internal;

public enum PaymentFailureReason {
    PROCESSING_FAILED(
        "PAYMENT_PROCESSING_FAILED"
    ),
    CONCURRENT_MODIFICATION(
        "PAYMENT_CONCURRENT_MODIFICATION"
    );

    private final String code;

    PaymentFailureReason(
        String code
    ) {
        this.code = code;
    }

    public String code() {
        return code;
    }
}
