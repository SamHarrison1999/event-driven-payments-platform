package com.samharrison.payments.customer.internal;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

interface CustomerProfileRepository
    extends JpaRepository<CustomerProfile, UUID> {
}