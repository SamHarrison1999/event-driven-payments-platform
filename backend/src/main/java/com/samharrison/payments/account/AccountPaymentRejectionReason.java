package com.samharrison.payments.account;

public enum AccountPaymentRejectionReason {
    SOURCE_NOT_OWNED,
    SOURCE_NOT_FOUND,
    DESTINATION_NOT_FOUND,
    SOURCE_NOT_ACTIVE,
    DESTINATION_NOT_ACTIVE,
    CURRENCY_MISMATCH,
    INSUFFICIENT_FUNDS
}
