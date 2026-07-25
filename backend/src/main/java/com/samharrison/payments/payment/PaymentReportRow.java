package com.samharrison.payments.payment;

import java.time.Instant;
import java.util.UUID;

public record PaymentReportRow(
    UUID paymentId,
    UUID actorIdentityUserId,
    UUID sourceAccountId,
    UUID destinationAccountId,
    long amountMinorUnits,
    String currency,
    String status,
    UUID ledgerTransactionId,
    String rejectionCode,
    String failureCode,
    Instant createdAt,
    Instant updatedAt
) {
}
