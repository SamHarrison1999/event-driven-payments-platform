package com.samharrison.payments.customer.internal;

import java.time.Instant;
import java.util.UUID;

public record CustomerSnapshot(
    UUID id,
    String fullName,
    CustomerStatus status,
    Instant createdAt,
    Instant updatedAt,
    long version
) {

    static CustomerSnapshot from(
        CustomerProfile customer
    ) {
        return new CustomerSnapshot(
            customer.id(),
            customer.fullName(),
            customer.status(),
            customer.createdAt(),
            customer.updatedAt(),
            customer.version()
        );
    }
}