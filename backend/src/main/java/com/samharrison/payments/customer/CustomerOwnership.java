package com.samharrison.payments.customer;

import java.util.UUID;

public interface CustomerOwnership {

    UUID requireCustomerId(
        UUID identityUserId
    );
}