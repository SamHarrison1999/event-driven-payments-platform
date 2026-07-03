package com.samharrison.payments.payment.internal;

public enum PaymentStatus {
    PENDING,
    PROCESSING,
    COMPLETED,
    REJECTED,
    FAILED;

    public boolean isTerminal() {
        return switch (this) {
            case COMPLETED, REJECTED, FAILED -> true;
            case PENDING, PROCESSING -> false;
        };
    }
}
