package com.samharrison.payments.customer.internal;

import java.time.Instant;
import java.util.UUID;

public record CustomerResponse(
    UUID id,
    String fullName,
    CustomerStatus status,
    Instant createdAt,
    Instant updatedAt,
    long version
) {

    static CustomerResponse from(
        CustomerSnapshot customer
    ) {
        return new CustomerResponse(
            customer.id(),
            customer.fullName(),
            customer.status(),
            customer.createdAt(),
            customer.updatedAt(),
            customer.version()
        );
    }
}