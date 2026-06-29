package com.samharrison.payments.customer.internal;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(
    name = "customer_identity_assignment",
    indexes = {
        @Index(
            name =
                "idx_customer_identity_assignment_customer",
            columnList = "customer_id"
        )
    }
)
class CustomerIdentityAssignment {

    @Id
    @Column(
        name = "identity_user_id",
        nullable = false,
        updatable = false
    )
    private UUID identityUserId;

    @Column(
        name = "customer_id",
        nullable = false,
        updatable = false
    )
    private UUID customerId;

    @Column(
        name = "assigned_at",
        nullable = false,
        updatable = false
    )
    private Instant assignedAt;

    @Version
    @Column(
        name = "version",
        nullable = false
    )
    private long version;

    protected CustomerIdentityAssignment() {
        // Required by JPA.
    }

    private CustomerIdentityAssignment(
        UUID identityUserId,
        UUID customerId,
        Instant assignedAt
    ) {
        this.identityUserId =
            Objects.requireNonNull(
                identityUserId,
                "identityUserId must not be null"
            );

        this.customerId =
            Objects.requireNonNull(
                customerId,
                "customerId must not be null"
            );

        this.assignedAt =
            Objects.requireNonNull(
                assignedAt,
                "assignedAt must not be null"
            );
    }

    static CustomerIdentityAssignment assign(
        UUID identityUserId,
        UUID customerId,
        Instant assignedAt
    ) {
        return new CustomerIdentityAssignment(
            identityUserId,
            customerId,
            assignedAt
        );
    }

    UUID identityUserId() {
        return identityUserId;
    }

    UUID customerId() {
        return customerId;
    }

    Instant assignedAt() {
        return assignedAt;
    }

    long version() {
        return version;
    }
}