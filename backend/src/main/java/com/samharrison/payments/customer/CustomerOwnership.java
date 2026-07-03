package com.samharrison.payments.customer;

import java.util.Optional;
import java.util.UUID;

public interface CustomerOwnership {

    Optional<UUID> findCustomerId(
        UUID identityUserId
    );

    UUID requireCustomerId(
        UUID identityUserId
    );
}