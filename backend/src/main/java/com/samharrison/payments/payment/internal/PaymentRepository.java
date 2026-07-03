package com.samharrison.payments.payment.internal;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

interface PaymentRepository
    extends JpaRepository<Payment, UUID> {
}