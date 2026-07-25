package com.samharrison.payments.account.internal;

import static com.samharrison.payments.account.internal.AccountCurrency.GBP;
import static com.samharrison.payments.account.internal.AccountStatus.ACTIVE;
import static com.samharrison.payments.account.internal.AccountStatus.CLOSED;
import static com.samharrison.payments.account.internal.AccountStatus.FROZEN;
import static com.samharrison.payments.customer.CustomerAccountEligibilityException.Reason.NOT_ACTIVE;
import static com.samharrison.payments.customer.CustomerAccountEligibilityException.Reason.NOT_FOUND;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.samharrison.payments.customer.CustomerAccountEligibilityException;
import com.samharrison.payments.shared.GbpAmount;
import java.time.Instant;
import java.time.ZoneOffset;
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
class AccountManagementServiceIntegrationTest {

    private static final UUID ACTOR_ID =
        UUID.fromString(
            "ced219ea-340f-4d14-a4bd-a3a38517298e"
        );

    @Container
    @ServiceConnection
    static final PostgreSQLContainer POSTGRESQL =
        new PostgreSQLContainer(
            "postgres:18.4-alpine"
        )
            .withDatabaseName(
                "payments_account_management_test"
            )
            .withUsername("payments_test")
            .withPassword("payments_test_only");

    @Autowired
    private AccountManagementService service;

    @Autowired
    private CustomerAccountRepository repository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    @WithMockUser(roles = "OPERATIONS")
    void operationsUserCreatesAndFindsAccount() {
        UUID customerId =
            insertCustomer("ACTIVE");

        AccountSnapshot created =
            create(customerId);

        assertThat(created.id())
            .isNotNull();

        assertThat(created.customerId())
            .isEqualTo(customerId);

        assertThat(created.currency())
            .isEqualTo(GBP);

        assertThat(created.balance())
            .isEqualTo(GbpAmount.ZERO);

        assertThat(created.status())
            .isEqualTo(ACTIVE);

        assertThat(created.version())
            .isZero();

        assertThat(created.createdAt())
            .isNotNull();

        assertThat(created.updatedAt())
            .isEqualTo(created.createdAt());

        AccountSnapshot found =
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
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void administratorCreatesAccount() {
        UUID customerId =
            insertCustomer("ACTIVE");

        AccountSnapshot created =
            create(customerId);

        assertThat(created.customerId())
            .isEqualTo(customerId);

        assertThat(created.status())
            .isEqualTo(ACTIVE);
    }

    @Test
    @WithMockUser(roles = "OPERATIONS")
    void operationsUserManagesAccountLifecycle() {
        UUID customerId =
            insertCustomer("ACTIVE");

        AccountSnapshot created =
            create(customerId);

        AccountSnapshot frozen =
            freeze(
                created.id(),
                created.version()
            );

        assertThat(frozen.status())
            .isEqualTo(FROZEN);

        assertThat(frozen.version())
            .isEqualTo(
                created.version() + 1
            );

        AccountSnapshot reactivated =
            reactivate(
                created.id(),
                frozen.version()
            );

        assertThat(reactivated.status())
            .isEqualTo(ACTIVE);

        assertThat(reactivated.version())
            .isEqualTo(
                frozen.version() + 1
            );

        AccountSnapshot closed =
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
                reactivate(
                    created.id(),
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
        UUID customerId =
            insertCustomer("ACTIVE");

        AccountSnapshot created =
            create(customerId);

        AccountSnapshot firstFreeze =
            freeze(
                created.id(),
                created.version()
            );

        AccountSnapshot secondFreeze =
            freeze(
                created.id(),
                firstFreeze.version()
            );

        assertThat(secondFreeze.status())
            .isEqualTo(FROZEN);

        assertThat(secondFreeze.updatedAt())
            .isEqualTo(
                firstFreeze.updatedAt()
            );

        assertThat(secondFreeze.version())
            .isEqualTo(
                firstFreeze.version()
            );

        AccountSnapshot firstReactivation =
            reactivate(
                created.id(),
                secondFreeze.version()
            );

        AccountSnapshot secondReactivation =
            reactivate(
                created.id(),
                firstReactivation.version()
            );

        assertThat(secondReactivation.status())
            .isEqualTo(ACTIVE);

        assertThat(secondReactivation.updatedAt())
            .isEqualTo(
                firstReactivation.updatedAt()
            );

        assertThat(secondReactivation.version())
            .isEqualTo(
                firstReactivation.version()
            );

        AccountSnapshot firstClosure =
            close(
                created.id(),
                secondReactivation.version()
            );

        AccountSnapshot secondClosure =
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
            .isEqualTo(4L);
    }

    @Test
    @WithMockUser(roles = "OPERATIONS")
    void missingCustomerCannotReceiveAccount() {
        UUID missingCustomerId =
            UUID.randomUUID();

        assertThatThrownBy(
            () ->
                create(
                    missingCustomerId
                )
        )
            .isInstanceOfSatisfying(
                CustomerAccountEligibilityException.class,
                exception -> {
                    assertThat(exception.customerId())
                        .isEqualTo(
                            missingCustomerId
                        );

                    assertThat(exception.reason())
                        .isEqualTo(NOT_FOUND);
                }
            );

        assertThat(repository.count())
            .isZero();
    }

    @Test
    @WithMockUser(roles = "OPERATIONS")
    void inactiveCustomersCannotReceiveAccount() {
        UUID suspendedCustomerId =
            insertCustomer("SUSPENDED");

        UUID closedCustomerId =
            insertCustomer("CLOSED");

        assertThatThrownBy(
            () ->
                create(
                    suspendedCustomerId
                )
        )
            .isInstanceOfSatisfying(
                CustomerAccountEligibilityException.class,
                exception ->
                    assertThat(exception.reason())
                        .isEqualTo(NOT_ACTIVE)
            );

        assertThatThrownBy(
            () ->
                create(
                    closedCustomerId
                )
        )
            .isInstanceOfSatisfying(
                CustomerAccountEligibilityException.class,
                exception ->
                    assertThat(exception.reason())
                        .isEqualTo(NOT_ACTIVE)
            );

        assertThat(repository.count())
            .isZero();
    }

    @Test
    @WithMockUser(roles = "OPERATIONS")
    void missingAccountIsReported() {
        UUID missingAccountId =
            UUID.randomUUID();

        assertThatThrownBy(
            () ->
                service.find(
                    missingAccountId
                )
        )
            .isInstanceOf(
                AccountNotFoundException.class
            )
            .hasMessageContaining(
                missingAccountId.toString()
            );
    }

    @Test
    @WithMockUser(roles = "CUSTOMER")
    void customerUserCannotManageAccounts() {
        assertThatThrownBy(
            () ->
                create(
                    UUID.randomUUID()
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
    void anonymousUserCannotManageAccounts() {
        assertThatThrownBy(
            () ->
                create(
                    UUID.randomUUID()
                )
        )
            .isInstanceOf(
                AccessDeniedException.class
            );

        assertThat(repository.count())
            .isZero();
    }

    private AccountSnapshot create(
        UUID customerId
    ) {
        return service.create(
            customerId,
            ACTOR_ID
        );
    }

    private Long auditEventCount(
        UUID accountId
    ) {
        return jdbcTemplate.queryForObject(
            """
            SELECT COUNT(*)
            FROM business_audit_event
            WHERE subject_type = 'account'
              AND subject_identifier = ?
            """,
            Long.class,
            accountId.toString()
        );
    }

    private AccountSnapshot freeze(
        UUID accountId,
        long expectedVersion
    ) {
        return service.freeze(
            accountId,
            expectedVersion,
            ACTOR_ID
        );
    }

    private AccountSnapshot reactivate(
        UUID accountId,
        long expectedVersion
    ) {
        return service.reactivate(
            accountId,
            expectedVersion,
            ACTOR_ID
        );
    }

    private AccountSnapshot close(
        UUID accountId,
        long expectedVersion
    ) {
        return service.close(
            accountId,
            expectedVersion,
            ACTOR_ID
        );
    }

    private UUID insertCustomer(
        String status
    ) {
        UUID customerId =
            UUID.randomUUID();

        Instant timestamp =
            Instant.parse(
                "2026-06-29T09:00:00Z"
            );

        jdbcTemplate.update(
            """
            INSERT INTO customer_profile (
                id,
                full_name,
                status,
                created_at,
                updated_at,
                version
            )
            VALUES (?, ?, ?, ?, ?, ?)
            """,
            customerId,
            "Account Management Customer",
            status,
            timestamp.atOffset(
                ZoneOffset.UTC
            ),
            timestamp.atOffset(
                ZoneOffset.UTC
            ),
            0L
        );

        return customerId;
    }
}
