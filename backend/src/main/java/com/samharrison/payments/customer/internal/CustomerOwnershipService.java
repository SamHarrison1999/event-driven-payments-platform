package com.samharrison.payments.customer.internal;

import com.samharrison.payments.customer.CustomerOwnership;
import com.samharrison.payments.customer.CustomerOwnershipNotFoundException;
import java.util.Objects;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
class CustomerOwnershipService
    implements CustomerOwnership {

    private final CustomerIdentityAssignmentRepository
        repository;

    CustomerOwnershipService(
        CustomerIdentityAssignmentRepository repository
    ) {
        this.repository = repository;
    }

    @Override
    @Transactional(readOnly = true)
    public UUID requireCustomerId(
        UUID identityUserId
    ) {
        UUID requiredIdentityUserId =
            Objects.requireNonNull(
                identityUserId,
                "identityUserId must not be null"
            );

        return repository
            .findById(requiredIdentityUserId)
            .map(
                CustomerIdentityAssignment::customerId
            )
            .orElseThrow(
                () ->
                    new CustomerOwnershipNotFoundException(
                        requiredIdentityUserId
                    )
            );
    }
}