package com.samharrison.payments.account.internal;

import com.samharrison.payments.audit.BusinessAuditEvents;
import com.samharrison.payments.audit.BusinessAuditRecorder;
import com.samharrison.payments.customer.CustomerAccountEligibility;
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
public class AccountManagementService {

    private final CustomerAccountRepository repository;

    private final CustomerAccountEligibility
        customerEligibility;

    private final BusinessAuditRecorder auditRecorder;

    private final Clock clock;

    public AccountManagementService(
        CustomerAccountRepository repository,
        CustomerAccountEligibility customerEligibility,
        BusinessAuditRecorder auditRecorder,
        Clock clock
    ) {
        this.repository = repository;
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
    public AccountSnapshot create(
        UUID customerId,
        UUID actorIdentityUserId
    ) {
        UUID requiredCustomerId =
            Objects.requireNonNull(
                customerId,
                "customerId must not be null"
            );

        customerEligibility.requireEligible(
            requiredCustomerId
        );

        CustomerAccount account =
            CustomerAccount.create(
                requiredCustomerId,
                now()
            );

        repository.save(account);

        AccountSnapshot snapshot =
            flushAndSnapshot(account);

        auditRecorder.record(
            BusinessAuditEvents.accountCreated(
                snapshot.createdAt(),
                actorIdentityUserId,
                snapshot.id(),
                snapshot.customerId()
            )
        );

        return snapshot;
    }

    @Transactional(readOnly = true)
    public AccountSnapshot find(
        UUID accountId
    ) {
        return AccountSnapshot.from(
            findRequired(accountId)
        );
    }

    @Transactional
    public AccountSnapshot freeze(
        UUID accountId,
        long expectedVersion,
        UUID actorIdentityUserId
    ) {
        CustomerAccount account =
            findRequired(accountId);

        requireExpectedVersion(
            account,
            expectedVersion
        );

        AccountStatus previousStatus =
            account.status();

        account.freeze(now());

        return flushAuditAndSnapshot(
            account,
            previousStatus,
            actorIdentityUserId
        );
    }

    @Transactional
    public AccountSnapshot reactivate(
        UUID accountId,
        long expectedVersion,
        UUID actorIdentityUserId
    ) {
        CustomerAccount account =
            findRequired(accountId);

        requireExpectedVersion(
            account,
            expectedVersion
        );

        AccountStatus previousStatus =
            account.status();

        account.reactivate(now());

        return flushAuditAndSnapshot(
            account,
            previousStatus,
            actorIdentityUserId
        );
    }

    @Transactional
    public AccountSnapshot close(
        UUID accountId,
        long expectedVersion,
        UUID actorIdentityUserId
    ) {
        CustomerAccount account =
            findRequired(accountId);

        requireExpectedVersion(
            account,
            expectedVersion
        );

        AccountStatus previousStatus =
            account.status();

        account.close(now());

        return flushAuditAndSnapshot(
            account,
            previousStatus,
            actorIdentityUserId
        );
    }

    private CustomerAccount findRequired(
        UUID accountId
    ) {
        UUID requiredAccountId =
            Objects.requireNonNull(
                accountId,
                "accountId must not be null"
            );

        return repository
            .findById(requiredAccountId)
            .orElseThrow(
                () ->
                    new AccountNotFoundException(
                        requiredAccountId
                    )
            );
    }

    private static void requireExpectedVersion(
        CustomerAccount account,
        long expectedVersion
    ) {
        if (expectedVersion < 0L) {
            throw new IllegalArgumentException(
                "expectedVersion must not be negative"
            );
        }

        if (account.version() != expectedVersion) {
            throw new AccountVersionConflictException(
                account.id(),
                expectedVersion,
                account.version()
            );
        }
    }

    private AccountSnapshot flushAndSnapshot(
        CustomerAccount account
    ) {
        repository.flush();

        return AccountSnapshot.from(account);
    }

    private AccountSnapshot flushAuditAndSnapshot(
        CustomerAccount account,
        AccountStatus previousStatus,
        UUID actorIdentityUserId
    ) {
        AccountSnapshot snapshot =
            flushAndSnapshot(account);

        if (previousStatus != snapshot.status()) {
            auditRecorder.record(
                BusinessAuditEvents.accountStatusChanged(
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
