package com.samharrison.payments.account.internal;

import com.samharrison.payments.customer.CustomerOwnership;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CustomerAccountQueryService {

    private final CustomerAccountRepository repository;

    private final CustomerOwnership ownership;

    public CustomerAccountQueryService(
        CustomerAccountRepository repository,
        CustomerOwnership ownership
    ) {
        this.repository = repository;
        this.ownership = ownership;
    }

    @Transactional(readOnly = true)
    @PreAuthorize("hasRole('CUSTOMER')")
    public List<AccountSnapshot>
    findOwnedByIdentityUser(
        UUID identityUserId
    ) {
        UUID requiredIdentityUserId =
            Objects.requireNonNull(
                identityUserId,
                "identityUserId must not be null"
            );

        UUID customerId =
            ownership.requireCustomerId(
                requiredIdentityUserId
            );

        return findByCustomerIdInternal(
            customerId
        );
    }

    @Transactional(readOnly = true)
    @PreAuthorize(
        "hasAnyRole('OPERATIONS', 'ADMIN')"
    )
    public List<AccountSnapshot> findByCustomerId(
        UUID customerId
    ) {
        return findByCustomerIdInternal(
            Objects.requireNonNull(
                customerId,
                "customerId must not be null"
            )
        );
    }

    private List<AccountSnapshot>
    findByCustomerIdInternal(
        UUID customerId
    ) {
        return repository
            .findAllByCustomerIdOrderByCreatedAtAscIdAsc(
                customerId
            )
            .stream()
            .map(AccountSnapshot::from)
            .toList();
    }
}