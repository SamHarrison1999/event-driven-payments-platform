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
import org.springframework.jdbc.core.JdbcTemplate;
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

    private static final UUID ACTOR_ID =
        UUID.fromString(
            "ab03dc4d-58b8-4b58-a56e-9773692e7dc0"
        );

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

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    @WithMockUser(roles = "OPERATIONS")
    void operationsUserCreatesAndFindsCustomer() {
        CustomerSnapshot created =
            create(
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

        assertThat(auditEventCount(created.id()))
            .isEqualTo(1L);

        assertThat(
            jdbcTemplate.queryForObject(
                """
                SELECT event_type
                FROM business_audit_event
                WHERE subject_identifier = ?
                """,
                String.class,
                created.id().toString()
            )
        )
            .isEqualTo("customer.created");
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void administratorCreatesCustomer() {
        CustomerSnapshot created =
            create("Admin Created");

        assertThat(created.fullName())
            .isEqualTo("Admin Created");

        assertThat(created.status())
            .isEqualTo(ACTIVE);
    }

    @Test
    @WithMockUser(roles = "OPERATIONS")
    void operationsUserManagesCustomerLifecycle() {
        CustomerSnapshot created =
            create("Sam Example");

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
            suspend(
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
            reactivate(
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
            close(
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

        assertThat(auditEventCount(created.id()))
            .isEqualTo(4L);
    }

    @Test
    @WithMockUser(roles = "OPERATIONS")
    void lifecycleOperationsAreIdempotent() {
        CustomerSnapshot created =
            create("Sam Example");

        CustomerSnapshot firstSuspension =
            suspend(
                created.id(),
                created.version()
            );

        CustomerSnapshot secondSuspension =
            suspend(
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
            close(
                created.id(),
                secondSuspension.version()
            );

        CustomerSnapshot secondClosure =
            close(
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

        assertThat(auditEventCount(created.id()))
            .isEqualTo(3L);
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
                create(
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
                create(
                    "Anonymous Customer"
                )
        )
            .isInstanceOf(
                AccessDeniedException.class
            );

        assertThat(repository.count())
            .isZero();
    }

    private CustomerSnapshot create(
        String fullName
    ) {
        return service.create(
            fullName,
            ACTOR_ID
        );
    }

    private Long auditEventCount(
        UUID customerId
    ) {
        return jdbcTemplate.queryForObject(
            """
            SELECT COUNT(*)
            FROM business_audit_event
            WHERE subject_type = 'customer'
              AND subject_identifier = ?
            """,
            Long.class,
            customerId.toString()
        );
    }

    private CustomerSnapshot suspend(
        UUID customerId,
        long expectedVersion
    ) {
        return service.suspend(
            customerId,
            expectedVersion,
            ACTOR_ID
        );
    }

    private CustomerSnapshot reactivate(
        UUID customerId,
        long expectedVersion
    ) {
        return service.reactivate(
            customerId,
            expectedVersion,
            ACTOR_ID
        );
    }

    private CustomerSnapshot close(
        UUID customerId,
        long expectedVersion
    ) {
        return service.close(
            customerId,
            expectedVersion,
            ACTOR_ID
        );
    }
}
