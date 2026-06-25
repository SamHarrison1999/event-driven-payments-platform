package com.samharrison.payments.identity.internal;

import static com.samharrison.payments.identity.internal.IdentityRole.CUSTOMER;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

@Entity
@Table(
    name = "identity_user",
    uniqueConstraints = {
        @UniqueConstraint(
            name = "uq_identity_user_normalized_email",
            columnNames = "normalized_email"
        )
    },
    indexes = {
        @Index(
            name = "idx_identity_user_status",
            columnList = "status"
        )
    }
)
public class IdentityUser {

    private static final int MAX_PASSWORD_HASH_LENGTH = 255;

    @Id
    @Column(
        name = "id",
        nullable = false,
        updatable = false
    )
    private UUID id;

    @Column(
        name = "email",
        nullable = false,
        length = EmailAddress.MAX_LENGTH
    )
    private String email;

    @Column(
        name = "normalized_email",
        nullable = false,
        length = EmailAddress.MAX_LENGTH
    )
    private String normalizedEmail;

    @Column(
        name = "password_hash",
        nullable = false,
        length = MAX_PASSWORD_HASH_LENGTH
    )
    private String passwordHash;

    @Enumerated(EnumType.STRING)
    @Column(
        name = "status",
        nullable = false,
        length = 32
    )
    private IdentityUserStatus status;

    @Column(
        name = "failed_login_attempts",
        nullable = false
    )
    private int failedLoginAttempts;

    @Column(name = "locked_until")
    private Instant lockedUntil;

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

    @ElementCollection(fetch = FetchType.LAZY)
    @CollectionTable(
        name = "identity_user_role",
        joinColumns = {
            @JoinColumn(
                name = "user_id",
                nullable = false
            )
        }
    )
    @Enumerated(EnumType.STRING)
    @Column(
        name = "role_code",
        nullable = false,
        length = 32
    )
    private Set<IdentityRole> roles = new HashSet<>();

    protected IdentityUser() {
        // Required by JPA.
    }

    private IdentityUser(
        UUID id,
        EmailAddress emailAddress,
        String passwordHash,
        Set<IdentityRole> roles,
        Instant registeredAt
    ) {
        this.id = Objects.requireNonNull(
            id,
            "id must not be null"
        );

        EmailAddress requiredEmail =
            Objects.requireNonNull(
                emailAddress,
                "emailAddress must not be null"
            );

        this.email = requiredEmail.value();
        this.normalizedEmail =
            requiredEmail.normalizedValue();

        this.passwordHash =
            requirePasswordHash(passwordHash);

        this.roles = new HashSet<>(
            Objects.requireNonNull(
                roles,
                "roles must not be null"
            )
        );

        if (this.roles.isEmpty()) {
            throw new IllegalArgumentException(
                "At least one role is required."
            );
        }

        this.status = IdentityUserStatus.ACTIVE;
        this.failedLoginAttempts = 0;
        this.lockedUntil = null;

        Instant timestamp = Objects.requireNonNull(
            registeredAt,
            "registeredAt must not be null"
        );

        this.createdAt = timestamp;
        this.updatedAt = timestamp;
    }

    public static IdentityUser registerCustomer(
        EmailAddress emailAddress,
        String passwordHash,
        Instant registeredAt
    ) {
        return new IdentityUser(
            UUID.randomUUID(),
            emailAddress,
            passwordHash,
            Set.of(CUSTOMER),
            registeredAt
        );
    }

    void recordFailedLogin(
        Instant attemptedAt,
        IdentityLockoutPolicy policy
    ) {
        Instant timestamp =
            Objects.requireNonNull(
                attemptedAt,
                "attemptedAt must not be null"
            );

        IdentityLockoutPolicy requiredPolicy =
            Objects.requireNonNull(
                policy,
                "policy must not be null"
            );

        releaseExpiredLock(timestamp);

        if (status != IdentityUserStatus.ACTIVE) {
            return;
        }

        failedLoginAttempts += 1;

        if (
            failedLoginAttempts
                >= requiredPolicy
                .maximumFailedAttempts()
        ) {
            status = IdentityUserStatus.LOCKED;
            lockedUntil =
                timestamp.plus(
                    requiredPolicy.lockDuration()
                );
        }

        updatedAt = timestamp;
    }

    void recordSuccessfulLogin(
        Instant authenticatedAt
    ) {
        Instant timestamp =
            Objects.requireNonNull(
                authenticatedAt,
                "authenticatedAt must not be null"
            );

        if (status != IdentityUserStatus.ACTIVE) {
            throw new IllegalStateException(
                "Only active users can complete "
                    + "authentication."
            );
        }

        failedLoginAttempts = 0;
        lockedUntil = null;
        updatedAt = timestamp;
    }

    boolean releaseExpiredLock(
        Instant checkedAt
    ) {
        Instant timestamp =
            Objects.requireNonNull(
                checkedAt,
                "checkedAt must not be null"
            );

        if (
            status != IdentityUserStatus.LOCKED
                || lockedUntil == null
                || timestamp.isBefore(lockedUntil)
        ) {
            return false;
        }

        status = IdentityUserStatus.ACTIVE;
        failedLoginAttempts = 0;
        lockedUntil = null;
        updatedAt = timestamp;

        return true;
    }

    private static String requirePasswordHash(
        String passwordHash
    ) {
        if (
            passwordHash == null
                || passwordHash.isBlank()
        ) {
            throw new IllegalArgumentException(
                "Password hash is required."
            );
        }

        if (
            passwordHash.length()
                > MAX_PASSWORD_HASH_LENGTH
        ) {
            throw new IllegalArgumentException(
                "Password hash must not exceed "
                    + MAX_PASSWORD_HASH_LENGTH
                    + " characters."
            );
        }

        return passwordHash;
    }

    public UUID id() {
        return id;
    }

    public String email() {
        return email;
    }

    public String normalizedEmail() {
        return normalizedEmail;
    }

    public IdentityUserStatus status() {
        return status;
    }

    public int failedLoginAttempts() {
        return failedLoginAttempts;
    }

    public Instant lockedUntil() {
        return lockedUntil;
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

    public Set<IdentityRole> roles() {
        return Set.copyOf(roles);
    }

    String passwordHash() {
        return passwordHash;
    }
}
