package com.samharrison.payments.customer.internal;

import com.samharrison.payments.audit.BusinessAuditEvents;
import com.samharrison.payments.audit.BusinessAuditRecorder;
import java.time.Clock;
import java.time.Instant;
import java.util.Objects;
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

    private final BusinessAuditRecorder auditRecorder;

    private final Clock clock;

    public CustomerManagementService(
        CustomerProfileRepository repository,
        BusinessAuditRecorder auditRecorder,
        Clock clock
    ) {
        this.repository =
            Objects.requireNonNull(
                repository,
                "repository must not be null"
            );
        this.auditRecorder =
            Objects.requireNonNull(
                auditRecorder,
                "auditRecorder must not be null"
            );
        this.clock =
            Objects.requireNonNull(
                clock,
                "clock must not be null"
            );
    }

    @Transactional
    public CustomerSnapshot create(
        String fullName,
        UUID actorIdentityUserId
    ) {
        CustomerProfile customer =
            CustomerProfile.create(
                CustomerName.of(fullName),
                now()
            );

        repository.save(customer);

        CustomerSnapshot snapshot =
            flushAndSnapshot(customer);

        auditRecorder.record(
            BusinessAuditEvents.customerCreated(
                snapshot.createdAt(),
                actorIdentityUserId,
                snapshot.id()
            )
        );

        return snapshot;
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
        long expectedVersion,
        UUID actorIdentityUserId
    ) {
        CustomerProfile customer =
            findRequired(customerId);

        requireExpectedVersion(
            customer,
            expectedVersion
        );

        CustomerStatus previousStatus =
            customer.status();

        customer.suspend(now());

        return flushAuditAndSnapshot(
            customer,
            previousStatus,
            actorIdentityUserId
        );
    }

    @Transactional
    public CustomerSnapshot reactivate(
        UUID customerId,
        long expectedVersion,
        UUID actorIdentityUserId
    ) {
        CustomerProfile customer =
            findRequired(customerId);

        requireExpectedVersion(
            customer,
            expectedVersion
        );

        CustomerStatus previousStatus =
            customer.status();

        customer.reactivate(now());

        return flushAuditAndSnapshot(
            customer,
            previousStatus,
            actorIdentityUserId
        );
    }

    @Transactional
    public CustomerSnapshot close(
        UUID customerId,
        long expectedVersion,
        UUID actorIdentityUserId
    ) {
        CustomerProfile customer =
            findRequired(customerId);

        requireExpectedVersion(
            customer,
            expectedVersion
        );

        CustomerStatus previousStatus =
            customer.status();

        customer.close(now());

        return flushAuditAndSnapshot(
            customer,
            previousStatus,
            actorIdentityUserId
        );
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

    private CustomerSnapshot flushAuditAndSnapshot(
        CustomerProfile customer,
        CustomerStatus previousStatus,
        UUID actorIdentityUserId
    ) {
        CustomerSnapshot snapshot =
            flushAndSnapshot(customer);

        if (previousStatus != snapshot.status()) {
            auditRecorder.record(
                BusinessAuditEvents
                    .customerStatusChanged(
                        snapshot.updatedAt(),
                        actorIdentityUserId,
                        snapshot.id(),
                        previousStatus.name(),
                        snapshot.status().name(),
                        snapshot.version()
                    )
            );
        }

        return snapshot;
    }

    private Instant now() {
        return Instant.now(clock);
    }
}
