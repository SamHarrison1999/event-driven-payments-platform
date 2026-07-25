package com.samharrison.payments.customer.internal;

import com.samharrison.payments.audit.BusinessAuditEvents;
import com.samharrison.payments.audit.BusinessAuditRecorder;
import com.samharrison.payments.customer.CustomerAccountEligibility;
import com.samharrison.payments.identity.IdentityUserDirectory;
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
public class CustomerOwnershipManagementService {

    private final CustomerIdentityAssignmentRepository
        repository;

    private final IdentityUserDirectory
        identityUserDirectory;

    private final CustomerAccountEligibility
        customerEligibility;

    private final BusinessAuditRecorder auditRecorder;

    private final Clock clock;

    public CustomerOwnershipManagementService(
        CustomerIdentityAssignmentRepository repository,
        IdentityUserDirectory identityUserDirectory,
        CustomerAccountEligibility customerEligibility,
        BusinessAuditRecorder auditRecorder,
        Clock clock
    ) {
        this.repository = repository;
        this.identityUserDirectory =
            identityUserDirectory;
        this.customerEligibility =
            customerEligibility;
        this.auditRecorder =
            Objects.requireNonNull(
                auditRecorder,
                "auditRecorder must not be null"
            );
        this.clock = clock;
    }

    @Transactional
    public CustomerOwnershipSnapshot assign(
        UUID identityUserId,
        UUID customerId,
        UUID actorIdentityUserId
    ) {
        UUID requiredIdentityUserId =
            Objects.requireNonNull(
                identityUserId,
                "identityUserId must not be null"
            );

        UUID requiredCustomerId =
            Objects.requireNonNull(
                customerId,
                "customerId must not be null"
            );

        identityUserDirectory.requireExists(
            requiredIdentityUserId
        );

        customerEligibility.requireEligible(
            requiredCustomerId
        );

        CustomerIdentityAssignment existing =
            repository
                .findById(requiredIdentityUserId)
                .orElse(null);

        if (existing != null) {
            if (
                existing.customerId()
                    .equals(requiredCustomerId)
            ) {
                return CustomerOwnershipSnapshot.from(
                    existing
                );
            }

            throw new CustomerOwnershipConflictException(
                requiredIdentityUserId,
                existing.customerId(),
                requiredCustomerId
            );
        }

        CustomerIdentityAssignment assignment =
            CustomerIdentityAssignment.assign(
                requiredIdentityUserId,
                requiredCustomerId,
                now()
            );

        repository.save(assignment);
        repository.flush();

        CustomerOwnershipSnapshot snapshot =
            CustomerOwnershipSnapshot.from(
                assignment
            );

        auditRecorder.record(
            BusinessAuditEvents
                .identityCustomerAssigned(
                    snapshot.assignedAt(),
                    actorIdentityUserId,
                    snapshot.identityUserId(),
                    snapshot.customerId()
                )
        );

        return snapshot;
    }

    private Instant now() {
        return Instant.now(clock);
    }
}
