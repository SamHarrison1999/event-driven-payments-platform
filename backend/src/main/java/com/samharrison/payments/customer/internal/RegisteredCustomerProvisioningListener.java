package com.samharrison.payments.customer.internal;

import com.samharrison.payments.audit.BusinessAuditEvents;
import com.samharrison.payments.audit.BusinessAuditRecorder;
import com.samharrison.payments.customer.CustomerOnboarded;
import com.samharrison.payments.identity.CustomerRegistered;
import java.time.Clock;
import java.time.Instant;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;

@Component
@ConditionalOnProperty(
    prefix = "platform.portfolio-demo.auto-provisioning",
    name = "enabled",
    havingValue = "true"
)
class RegisteredCustomerProvisioningListener {

    private static final String
        SELF_SERVICE_CUSTOMER_NAME =
        "Demo Customer";

    private final CustomerProfileRepository
        customerRepository;

    private final CustomerIdentityAssignmentRepository
        assignmentRepository;

    private final BusinessAuditRecorder auditRecorder;

    private final ApplicationEventPublisher eventPublisher;

    private final Clock clock;

    RegisteredCustomerProvisioningListener(
        CustomerProfileRepository customerRepository,
        CustomerIdentityAssignmentRepository
            assignmentRepository,
        BusinessAuditRecorder auditRecorder,
        ApplicationEventPublisher eventPublisher,
        Clock clock
    ) {
        this.customerRepository = customerRepository;
        this.assignmentRepository =
            assignmentRepository;
        this.auditRecorder = auditRecorder;
        this.eventPublisher = eventPublisher;
        this.clock = clock;
    }

    @EventListener
    public void onCustomerRegistered(
        CustomerRegistered event
    ) {
        if (
            assignmentRepository.existsById(
                event.identityUserId()
            )
        ) {
            return;
        }

        Instant createdAt =
            Instant.now(clock);

        CustomerProfile customer =
            CustomerProfile.create(
                CustomerName.of(
                    SELF_SERVICE_CUSTOMER_NAME
                ),
                createdAt
            );

        customerRepository.save(customer);
        customerRepository.flush();

        CustomerIdentityAssignment assignment =
            CustomerIdentityAssignment.assign(
                event.identityUserId(),
                customer.id(),
                createdAt
            );

        assignmentRepository.save(
            assignment
        );
        assignmentRepository.flush();

        auditRecorder.record(
            BusinessAuditEvents.customerCreated(
                createdAt,
                event.identityUserId(),
                customer.id()
            )
        );

        auditRecorder.record(
            BusinessAuditEvents
                .identityCustomerAssigned(
                    createdAt,
                    event.identityUserId(),
                    event.identityUserId(),
                    customer.id()
                )
        );

        eventPublisher.publishEvent(
            new CustomerOnboarded(
                event.identityUserId(),
                customer.id()
            )
        );
    }
}