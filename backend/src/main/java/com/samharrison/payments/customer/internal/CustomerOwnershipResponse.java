package com.samharrison.payments.customer.internal;

import java.time.Instant;
import java.util.UUID;

public record CustomerOwnershipResponse(
    UUID identityUserId,
    UUID customerId,
    Instant assignedAt,
    long version
) {

    static CustomerOwnershipResponse from(
        CustomerOwnershipSnapshot ownership
    ) {
        return new CustomerOwnershipResponse(
            ownership.identityUserId(),
            ownership.customerId(),
            ownership.assignedAt(),
            ownership.version()
        );
    }
}