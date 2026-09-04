package com.samharrison.payments.account.internal;

import com.samharrison.payments.audit.BusinessAuditEvents;
import com.samharrison.payments.audit.BusinessAuditRecorder;
import com.samharrison.payments.customer.CustomerOnboarded;
import com.samharrison.payments.shared.GbpAmount;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;

@Component
@ConditionalOnProperty(
    prefix = "platform.portfolio-demo.auto-provisioning",
    name = "enabled",
    havingValue = "true"
)
class CustomerDemoAccountProvisioningListener {

    private static final int
        REQUIRED_DEMO_ACCOUNTS = 2;

    private static final GbpAmount
        INITIAL_DEMO_BALANCE =
        GbpAmount.ofMinorUnits(
            100_000L
        );

    private final CustomerAccountRepository repository;

    private final BusinessAuditRecorder auditRecorder;

    private final Clock clock;

    CustomerDemoAccountProvisioningListener(
        CustomerAccountRepository repository,
        BusinessAuditRecorder auditRecorder,
        Clock clock
    ) {
        this.repository = repository;
        this.auditRecorder = auditRecorder;
        this.clock = clock;
    }

    @EventListener
    public void onCustomerOnboarded(
        CustomerOnboarded event
    ) {
        List<CustomerAccount> existing =
            repository
                .findAllByCustomerIdOrderByCreatedAtAscIdAsc(
                    event.customerId()
                );

        int accountsToCreate =
            Math.max(
                0,
                REQUIRED_DEMO_ACCOUNTS
                    - existing.size()
            );

        if (accountsToCreate == 0) {
            return;
        }

        Instant createdAt =
            Instant.now(clock);

        List<CustomerAccount> created =
            new ArrayList<>();

        for (
            int index = 0;
            index < accountsToCreate;
            index += 1
        ) {
            CustomerAccount account =
                CustomerAccount.create(
                    event.customerId(),
                    createdAt
                );

            account.credit(
                INITIAL_DEMO_BALANCE,
                createdAt
            );

            created.add(account);
        }

        repository.saveAll(created);
        repository.flush();

        for (
            CustomerAccount account : created
        ) {
            auditRecorder.record(
                BusinessAuditEvents.accountCreated(
                    createdAt,
                    event.identityUserId(),
                    account.id(),
                    event.customerId()
                )
            );
        }
    }
}