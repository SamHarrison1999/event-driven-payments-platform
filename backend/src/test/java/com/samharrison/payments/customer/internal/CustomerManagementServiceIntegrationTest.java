package com.samharrison.payments.customer.internal;

import static com.samharrison.payments.customer.internal.CustomerStatus.ACTIVE;
import static com.samharrison.payments.customer.internal.CustomerStatus.CLOSED;
import static com.samharrison.payments.customer.internal.CustomerStatus.SUSPENDED;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.test.context.support.WithAnonymousUser;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

@SpringBootTest
@Testcontainers
@Transactional
@DirtiesContext(
    classMode = DirtiesContext.ClassMode.AFTER_CLASS
)
class CustomerManagementServiceIntegrationTest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer POSTGRESQL =
        new PostgreSQLContainer(
            "postgres:18.4-alpine"
        )
            .withDatabaseName(
                "payments_customer_management_test"
            )
            .withUsername("payments_test")
            .withPassword("payments_test_only");

    @Autowired
    private CustomerManagementService service;

    @Autowired
    private CustomerProfileRepository repository;

    @Test
    @WithMockUser(roles = "OPERATIONS")
    void operationsUserCreatesAndFindsCustomer() {
        CustomerSnapshot created =
            service.create(
                "  Sam Example  "
            );

        assertThat(created.id())
            .isNotNull();

        assertThat(created.fullName())
            .isEqualTo("Sam Example");

        assertThat(created.status())
            .isEqualTo(ACTIVE);

        assertThat(created.version())
            .isZero();

        assertThat(created.createdAt())
            .isNotNull();

        assertThat(created.updatedAt())
            .isEqualTo(created.createdAt());

        CustomerSnapshot found =
            service.find(created.id());

        assertThat(found)
            .isEqualTo(created);

        assertThat(
            repository.existsById(
                created.id()
            )
        )
            .isTrue();
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void administratorCreatesCustomer() {
        CustomerSnapshot created =
            service.create("Admin Created");

        assertThat(created.fullName())
            .isEqualTo("Admin Created");

        assertThat(created.status())
            .isEqualTo(ACTIVE);
    }

    @Test
    @WithMockUser(roles = "OPERATIONS")
    void operationsUserManagesCustomerLifecycle() {
        CustomerSnapshot created =
            service.create("Sam Example");

        CustomerSnapshot renamed =
            service.rename(
                created.id(),
                "Samuel Example",
                created.version()
            );

        assertThat(renamed.fullName())
            .isEqualTo("Samuel Example");

        assertThat(renamed.version())
            .isEqualTo(
                created.version() + 1
            );

        CustomerSnapshot suspended =
            service.suspend(
                created.id(),
                renamed.version()
            );

        assertThat(suspended.status())
            .isEqualTo(SUSPENDED);

        assertThat(suspended.version())
            .isEqualTo(
                renamed.version() + 1
            );

        CustomerSnapshot reactivated =
            service.reactivate(
                created.id(),
                suspended.version()
            );

        assertThat(reactivated.status())
            .isEqualTo(ACTIVE);

        assertThat(reactivated.version())
            .isEqualTo(
                suspended.version() + 1
            );

        CustomerSnapshot closed =
            service.close(
                created.id(),
                reactivated.version()
            );

        assertThat(closed.status())
            .isEqualTo(CLOSED);

        assertThat(closed.version())
            .isEqualTo(
                reactivated.version() + 1
            );

        assertThatThrownBy(
            () ->
                service.rename(
                    created.id(),
                    "Closed Customer",
                    closed.version()
                )
        )
            .isInstanceOf(
                IllegalStateException.class
            );
    }

    @Test
    @WithMockUser(roles = "OPERATIONS")
    void lifecycleOperationsAreIdempotent() {
        CustomerSnapshot created =
            service.create("Sam Example");

        CustomerSnapshot firstSuspension =
            service.suspend(
                created.id(),
                created.version()
            );

        CustomerSnapshot secondSuspension =
            service.suspend(
                created.id(),
                firstSuspension.version()
            );

        assertThat(secondSuspension.status())
            .isEqualTo(SUSPENDED);

        assertThat(secondSuspension.updatedAt())
            .isEqualTo(
                firstSuspension.updatedAt()
            );

        assertThat(secondSuspension.version())
            .isEqualTo(
                firstSuspension.version()
            );

        CustomerSnapshot firstClosure =
            service.close(
                created.id(),
                secondSuspension.version()
            );

        CustomerSnapshot secondClosure =
            service.close(
                created.id(),
                firstClosure.version()
            );

        assertThat(secondClosure.status())
            .isEqualTo(CLOSED);

        assertThat(secondClosure.updatedAt())
            .isEqualTo(
                firstClosure.updatedAt()
            );

        assertThat(secondClosure.version())
            .isEqualTo(
                firstClosure.version()
            );
    }

    @Test
    @WithMockUser(roles = "OPERATIONS")
    void missingCustomerIsReported() {
        UUID missingId =
            UUID.randomUUID();

        assertThatThrownBy(
            () -> service.find(missingId)
        )
            .isInstanceOf(
                CustomerNotFoundException.class
            )
            .hasMessageContaining(
                missingId.toString()
            );
    }

    @Test
    @WithMockUser(roles = "CUSTOMER")
    void customerUserCannotManageCustomers() {
        assertThatThrownBy(
            () ->
                service.create(
                    "Forbidden Customer"
                )
        )
            .isInstanceOf(
                AccessDeniedException.class
            );

        assertThat(repository.count())
            .isZero();
    }

    @Test
    @WithAnonymousUser
    void anonymousUserCannotManageCustomers() {
        assertThatThrownBy(
            () ->
                service.create(
                    "Anonymous Customer"
                )
        )
            .isInstanceOf(
                AccessDeniedException.class
            );

        assertThat(repository.count())
            .isZero();
    }
}