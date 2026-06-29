package com.samharrison.payments.account.internal;

import com.samharrison.payments.shared.GbpAmount;
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
    name = "customer_account",
    indexes = {
        @Index(
            name = "idx_customer_account_customer",
            columnList = "customer_id"
        ),
        @Index(
            name = "idx_customer_account_status",
            columnList = "status"
        )
    }
)
public class CustomerAccount {

    @Id
    @Column(
        name = "id",
        nullable = false,
        updatable = false
    )
    private UUID id;

    @Column(
        name = "customer_id",
        nullable = false,
        updatable = false
    )
    private UUID customerId;

    @Enumerated(EnumType.STRING)
    @Column(
        name = "currency",
        nullable = false,
        updatable = false,
        length = 3
    )
    private AccountCurrency currency;

    @Column(
        name = "balance_minor_units",
        nullable = false
    )
    private long balanceMinorUnits;

    @Enumerated(EnumType.STRING)
    @Column(
        name = "status",
        nullable = false,
        length = 32
    )
    private AccountStatus status;

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

    protected CustomerAccount() {
        // Required by JPA.
    }

    private CustomerAccount(
        UUID id,
        UUID customerId,
        Instant createdAt
    ) {
        this.id = Objects.requireNonNull(
            id,
            "id must not be null"
        );

        this.customerId =
            Objects.requireNonNull(
                customerId,
                "customerId must not be null"
            );

        Instant timestamp =
            Objects.requireNonNull(
                createdAt,
                "createdAt must not be null"
            );

        currency = AccountCurrency.GBP;
        balanceMinorUnits = 0L;
        status = AccountStatus.ACTIVE;
        this.createdAt = timestamp;
        updatedAt = timestamp;
    }

    public static CustomerAccount create(
        UUID customerId,
        Instant createdAt
    ) {
        return new CustomerAccount(
            UUID.randomUUID(),
            customerId,
            createdAt
        );
    }

    boolean credit(
        GbpAmount amount,
        Instant changedAt
    ) {
        ensureNotClosed();

        GbpAmount requiredAmount =
            requirePositiveAmount(amount);

        Instant timestamp =
            requireChangeTime(changedAt);

        GbpAmount updatedBalance =
            balance().plus(requiredAmount);

        balanceMinorUnits =
            updatedBalance.minorUnits();

        updatedAt = timestamp;

        return true;
    }

    boolean debit(
        GbpAmount amount,
        Instant changedAt
    ) {
        ensureActive();

        GbpAmount requiredAmount =
            requirePositiveAmount(amount);

        GbpAmount currentBalance = balance();

        if (
            requiredAmount.compareTo(
                currentBalance
            ) > 0
        ) {
            throw new InsufficientFundsException(
                id,
                currentBalance,
                requiredAmount
            );
        }

        Instant timestamp =
            requireChangeTime(changedAt);

        balanceMinorUnits =
            currentBalance
                .minus(requiredAmount)
                .minorUnits();

        updatedAt = timestamp;

        return true;
    }

    boolean freeze(
        Instant changedAt
    ) {
        ensureNotClosed();

        if (status == AccountStatus.FROZEN) {
            return false;
        }

        Instant timestamp =
            requireChangeTime(changedAt);

        status = AccountStatus.FROZEN;
        updatedAt = timestamp;

        return true;
    }

    boolean reactivate(
        Instant changedAt
    ) {
        ensureNotClosed();

        if (status == AccountStatus.ACTIVE) {
            return false;
        }

        Instant timestamp =
            requireChangeTime(changedAt);

        status = AccountStatus.ACTIVE;
        updatedAt = timestamp;

        return true;
    }

    boolean close(
        Instant changedAt
    ) {
        if (status == AccountStatus.CLOSED) {
            return false;
        }

        if (balanceMinorUnits != 0L) {
            throw new IllegalStateException(
                "An account with a non-zero "
                    + "balance cannot be closed."
            );
        }

        Instant timestamp =
            requireChangeTime(changedAt);

        status = AccountStatus.CLOSED;
        updatedAt = timestamp;

        return true;
    }

    private GbpAmount requirePositiveAmount(
        GbpAmount amount
    ) {
        GbpAmount required =
            Objects.requireNonNull(
                amount,
                "amount must not be null"
            );

        if (!required.isPositive()) {
            throw new IllegalArgumentException(
                "Account transaction amount must "
                    + "be greater than zero."
            );
        }

        return required;
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

    private void ensureActive() {
        if (status != AccountStatus.ACTIVE) {
            throw new IllegalStateException(
                "Only an active account can be "
                    + "debited."
            );
        }
    }

    private void ensureNotClosed() {
        if (status == AccountStatus.CLOSED) {
            throw new IllegalStateException(
                "A closed account cannot be "
                    + "changed."
            );
        }
    }

    public UUID id() {
        return id;
    }

    public UUID customerId() {
        return customerId;
    }

    public AccountCurrency currency() {
        return currency;
    }

    public GbpAmount balance() {
        return GbpAmount.ofMinorUnits(
            balanceMinorUnits
        );
    }

    public AccountStatus status() {
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