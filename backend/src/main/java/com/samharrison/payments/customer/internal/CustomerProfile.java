package com.samharrison.payments.customer.internal;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(
    name = "customer_profile",
    indexes = {
        @Index(
            name = "idx_customer_profile_status",
            columnList = "status"
        )
    }
)
public class CustomerProfile {

    @Id
    @Column(
        name = "id",
        nullable = false,
        updatable = false
    )
    private UUID id;

    @Column(
        name = "full_name",
        nullable = false,
        length = CustomerName.MAX_LENGTH
    )
    private String fullName;

    @Enumerated(EnumType.STRING)
    @Column(
        name = "status",
        nullable = false,
        length = 32
    )
    private CustomerStatus status;

    @Column(
        name = "created_at",
        nullable = false,
        updatable = false
    )
    private Instant createdAt;

    @Column(
        name = "updated_at",
        nullable = false
    )
    private Instant updatedAt;

    @Version
    @Column(
        name = "version",
        nullable = false
    )
    private long version;

    protected CustomerProfile() {
        // Required by JPA.
    }

    private CustomerProfile(
        UUID id,
        CustomerName fullName,
        Instant createdAt
    ) {
        this.id = Objects.requireNonNull(
            id,
            "id must not be null"
        );

        this.fullName = Objects.requireNonNull(
            fullName,
            "fullName must not be null"
        ).value();

        Instant timestamp = Objects.requireNonNull(
            createdAt,
            "createdAt must not be null"
        );

        this.status = CustomerStatus.ACTIVE;
        this.createdAt = timestamp;
        this.updatedAt = timestamp;
    }

    public static CustomerProfile create(
        CustomerName fullName,
        Instant createdAt
    ) {
        return new CustomerProfile(
            UUID.randomUUID(),
            fullName,
            createdAt
        );
    }

    boolean rename(
        CustomerName newName,
        Instant changedAt
    ) {
        ensureNotClosed();

        String requiredName =
            Objects.requireNonNull(
                newName,
                "newName must not be null"
            ).value();

        if (fullName.equals(requiredName)) {
            return false;
        }

        Instant timestamp =
            requireChangeTime(changedAt);

        fullName = requiredName;
        updatedAt = timestamp;

        return true;
    }

    boolean suspend(
        Instant changedAt
    ) {
        ensureNotClosed();

        if (status == CustomerStatus.SUSPENDED) {
            return false;
        }

        Instant timestamp =
            requireChangeTime(changedAt);

        status = CustomerStatus.SUSPENDED;
        updatedAt = timestamp;

        return true;
    }

    boolean reactivate(
        Instant changedAt
    ) {
        ensureNotClosed();

        if (status == CustomerStatus.ACTIVE) {
            return false;
        }

        Instant timestamp =
            requireChangeTime(changedAt);

        status = CustomerStatus.ACTIVE;
        updatedAt = timestamp;

        return true;
    }

    boolean close(
        Instant changedAt
    ) {
        if (status == CustomerStatus.CLOSED) {
            return false;
        }

        Instant timestamp =
            requireChangeTime(changedAt);

        status = CustomerStatus.CLOSED;
        updatedAt = timestamp;

        return true;
    }

    private Instant requireChangeTime(
        Instant changedAt
    ) {
        Instant timestamp =
            Objects.requireNonNull(
                changedAt,
                "changedAt must not be null"
            );

        if (timestamp.isBefore(updatedAt)) {
            throw new IllegalArgumentException(
                "Change time must not be before "
                    + "the previous update time."
            );
        }

        return timestamp;
    }

    private void ensureNotClosed() {
        if (status == CustomerStatus.CLOSED) {
            throw new IllegalStateException(
                "A closed customer cannot be changed."
            );
        }
    }

    public UUID id() {
        return id;
    }

    public String fullName() {
        return fullName;
    }

    public CustomerStatus status() {
        return status;
    }

    public Instant createdAt() {
        return createdAt;
    }

    public Instant updatedAt() {
        return updatedAt;
    }

    public long version() {
        return version;
    }
}