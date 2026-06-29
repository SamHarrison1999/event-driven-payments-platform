package com.samharrison.payments.customer.internal;

import java.time.Instant;
import java.util.UUID;

public record CustomerOwnershipSnapshot(
    UUID identityUserId,
    UUID customerId,
    Instant assignedAt,
    long version
) {

    static CustomerOwnershipSnapshot from(
        CustomerIdentityAssignment assignment
    ) {
        return new CustomerOwnershipSnapshot(
            assignment.identityUserId(),
            assignment.customerId(),
            assignment.assignedAt(),
            assignment.version()
        );
    }
}