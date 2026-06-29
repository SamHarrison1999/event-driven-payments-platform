package com.samharrison.payments.customer.internal;

import java.time.Clock;
import java.time.Instant;
import java.util.UUID;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@PreAuthorize(
    "hasAnyRole('OPERATIONS', 'ADMIN')"
)
public class CustomerManagementService {

    private final CustomerProfileRepository repository;

    private final Clock clock;

    public CustomerManagementService(
        CustomerProfileRepository repository,
        Clock clock
    ) {
        this.repository = repository;
        this.clock = clock;
    }

    @Transactional
    public CustomerSnapshot create(
        String fullName
    ) {
        CustomerProfile customer =
            CustomerProfile.create(
                CustomerName.of(fullName),
                now()
            );

        repository.save(customer);

        return flushAndSnapshot(customer);
    }

    @Transactional(readOnly = true)
    public CustomerSnapshot find(
        UUID customerId
    ) {
        return CustomerSnapshot.from(
            findRequired(customerId)
        );
    }

    @Transactional
    public CustomerSnapshot rename(
        UUID customerId,
        String fullName,
        long expectedVersion
    ) {
        CustomerProfile customer =
            findRequired(customerId);

        requireExpectedVersion(
            customer,
            expectedVersion
        );

        customer.rename(
            CustomerName.of(fullName),
            now()
        );

        return flushAndSnapshot(customer);
    }

    @Transactional
    public CustomerSnapshot suspend(
        UUID customerId,
        long expectedVersion
    ) {
        CustomerProfile customer =
            findRequired(customerId);

        requireExpectedVersion(
            customer,
            expectedVersion
        );

        customer.suspend(now());

        return flushAndSnapshot(customer);
    }

    @Transactional
    public CustomerSnapshot reactivate(
        UUID customerId,
        long expectedVersion
    ) {
        CustomerProfile customer =
            findRequired(customerId);

        requireExpectedVersion(
            customer,
            expectedVersion
        );

        customer.reactivate(now());

        return flushAndSnapshot(customer);
    }

    @Transactional
    public CustomerSnapshot close(
        UUID customerId,
        long expectedVersion
    ) {
        CustomerProfile customer =
            findRequired(customerId);

        requireExpectedVersion(
            customer,
            expectedVersion
        );

        customer.close(now());

        return flushAndSnapshot(customer);
    }

    private CustomerProfile findRequired(
        UUID customerId
    ) {
        return repository
            .findById(customerId)
            .orElseThrow(
                () ->
                    new CustomerNotFoundException(
                        customerId
                    )
            );
    }

    private static void requireExpectedVersion(
        CustomerProfile customer,
        long expectedVersion
    ) {
        if (expectedVersion < 0L) {
            throw new IllegalArgumentException(
                "expectedVersion must not be negative"
            );
        }

        if (customer.version() != expectedVersion) {
            throw new CustomerVersionConflictException(
                customer.id(),
                expectedVersion,
                customer.version()
            );
        }
    }

    private CustomerSnapshot flushAndSnapshot(
        CustomerProfile customer
    ) {
        repository.flush();

        return CustomerSnapshot.from(customer);
    }

    private Instant now() {
        return Instant.now(clock);
    }
}