package com.samharrison.payments.identity;

import java.util.Objects;
import java.util.UUID;

public record CustomerRegistered(
    UUID identityUserId
) {

    public CustomerRegistered {
        identityUserId =
            Objects.requireNonNull(
                identityUserId,
                "identityUserId must not be null"
            );
    }
}