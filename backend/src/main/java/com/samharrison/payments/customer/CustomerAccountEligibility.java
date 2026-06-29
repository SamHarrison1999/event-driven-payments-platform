package com.samharrison.payments.customer;

import java.util.UUID;

public interface CustomerAccountEligibility {

    void requireEligible(
        UUID customerId
    );
}