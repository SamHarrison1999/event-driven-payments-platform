package com.samharrison.payments.account.internal;

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

    private final Clock clock;

    public AccountManagementService(
        CustomerAccountRepository repository,
        CustomerAccountEligibility customerEligibility,
        Clock clock
    ) {
        this.repository = repository;
        this.customerEligibility =
            customerEligibility;
        this.clock = clock;
    }

    @Transactional
    public AccountSnapshot create(
        UUID customerId
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

        return flushAndSnapshot(account);
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
        UUID accountId
    ) {
        CustomerAccount account =
            findRequired(accountId);

        account.freeze(now());

        return flushAndSnapshot(account);
    }

    @Transactional
    public AccountSnapshot reactivate(
        UUID accountId
    ) {
        CustomerAccount account =
            findRequired(accountId);

        account.reactivate(now());

        return flushAndSnapshot(account);
    }

    @Transactional
    public AccountSnapshot close(
        UUID accountId
    ) {
        CustomerAccount account =
            findRequired(accountId);

        account.close(now());

        return flushAndSnapshot(account);
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

    private AccountSnapshot flushAndSnapshot(
        CustomerAccount account
    ) {
        repository.flush();

        return AccountSnapshot.from(account);
    }

    private Instant now() {
        return Instant.now(clock);
    }
}