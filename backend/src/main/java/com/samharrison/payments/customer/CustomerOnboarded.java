package com.samharrison.payments.customer;

import java.util.Objects;
import java.util.UUID;

public record CustomerOnboarded(
    UUID identityUserId,
    UUID customerId
) {

    public CustomerOnboarded {
        identityUserId =
            Objects.requireNonNull(
                identityUserId,
                "identityUserId must not be null"
            );

        customerId =
            Objects.requireNonNull(
                customerId,
                "customerId must not be null"
            );
    }
}