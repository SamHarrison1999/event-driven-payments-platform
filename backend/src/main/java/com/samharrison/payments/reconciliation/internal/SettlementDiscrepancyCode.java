package com.samharrison.payments.reconciliation.internal;

enum SettlementDiscrepancyCode {
    PAYMENT_NOT_FOUND,
    PAYMENT_NOT_COMPLETED,
    CURRENCY_MISMATCH,
    AMOUNT_MISMATCH,
    SETTLED_BEFORE_COMPLETION,
    DUPLICATE_PAYMENT_SETTLEMENT
}
