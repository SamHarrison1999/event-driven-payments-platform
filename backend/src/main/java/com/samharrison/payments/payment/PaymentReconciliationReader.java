package com.samharrison.payments.payment;

import java.util.Map;
import java.util.Set;
import java.util.UUID;

public interface PaymentReconciliationReader {

    int MAX_PAYMENT_IDS = 1_000;

    Map<UUID, PaymentReconciliationSnapshot> findAll(
        Set<UUID> paymentIds
    );
}
