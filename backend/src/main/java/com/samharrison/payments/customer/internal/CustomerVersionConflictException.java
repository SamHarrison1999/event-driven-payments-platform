package com.samharrison.payments.customer.internal;

import java.io.Serial;
import java.util.UUID;

public final class CustomerVersionConflictException
    extends RuntimeException {

    @Serial
    private static final long serialVersionUID = 1L;

    private final UUID customerId;
    private final long expectedVersion;
    private final long actualVersion;

    public CustomerVersionConflictException(
        UUID customerId,
        long expectedVersion,
        long actualVersion
    ) {
        super(
            "Customer "
                + customerId
                + " has version "
                + actualVersion
                + ", not the expected version "
                + expectedVersion
                + "."
        );

        this.customerId = customerId;
        this.expectedVersion = expectedVersion;
        this.actualVersion = actualVersion;
    }

    public UUID customerId() {
        return customerId;
    }

    public long expectedVersion() {
        return expectedVersion;
    }

    public long actualVersion() {
        return actualVersion;
    }
}