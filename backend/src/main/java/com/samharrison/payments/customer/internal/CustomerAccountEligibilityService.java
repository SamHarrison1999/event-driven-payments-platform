package com.samharrison.payments.customer.internal;

import static com.samharrison.payments.customer.CustomerAccountEligibilityException.Reason.NOT_ACTIVE;
import static com.samharrison.payments.customer.CustomerAccountEligibilityException.Reason.NOT_FOUND;

import com.samharrison.payments.customer.CustomerAccountEligibility;
import com.samharrison.payments.customer.CustomerAccountEligibilityException;
import java.util.Objects;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
class CustomerAccountEligibilityService
    implements CustomerAccountEligibility {

    private final CustomerProfileRepository repository;

    CustomerAccountEligibilityService(
        CustomerProfileRepository repository
    ) {
        this.repository = repository;
    }

    @Override
    @Transactional(readOnly = true)
    public void requireEligible(
        UUID customerId
    ) {
        UUID requiredCustomerId =
            Objects.requireNonNull(
                customerId,
                "customerId must not be null"
            );

        CustomerProfile customer =
            repository
                .findById(requiredCustomerId)
                .orElseThrow(
                    () ->
                        new CustomerAccountEligibilityException(
                            requiredCustomerId,
                            NOT_FOUND
                        )
                );

        if (
            customer.status()
                != CustomerStatus.ACTIVE
        ) {
            throw new CustomerAccountEligibilityException(
                requiredCustomerId,
                NOT_ACTIVE
            );
        }
    }
}