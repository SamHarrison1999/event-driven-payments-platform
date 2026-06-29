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
            service.create(customerId);

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
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void administratorCreatesAccount() {
        UUID customerId =
            insertCustomer("ACTIVE");

        AccountSnapshot created =
            service.create(customerId);

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
            service.create(customerId);

        AccountSnapshot frozen =
            service.freeze(created.id());

        assertThat(frozen.status())
            .isEqualTo(FROZEN);

        assertThat(frozen.version())
            .isEqualTo(
                created.version() + 1
            );

        AccountSnapshot reactivated =
            service.reactivate(created.id());

        assertThat(reactivated.status())
            .isEqualTo(ACTIVE);

        assertThat(reactivated.version())
            .isEqualTo(
                frozen.version() + 1
            );

        AccountSnapshot closed =
            service.close(created.id());

        assertThat(closed.status())
            .isEqualTo(CLOSED);

        assertThat(closed.version())
            .isEqualTo(
                reactivated.version() + 1
            );

        assertThatThrownBy(
            () ->
                service.reactivate(
                    created.id()
                )
        )
            .isInstanceOf(
                IllegalStateException.class
            );
    }

    @Test
    @WithMockUser(roles = "OPERATIONS")
    void lifecycleOperationsAreIdempotent() {
        UUID customerId =
            insertCustomer("ACTIVE");

        AccountSnapshot created =
            service.create(customerId);

        AccountSnapshot firstFreeze =
            service.freeze(created.id());

        AccountSnapshot secondFreeze =
            service.freeze(created.id());

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
            service.reactivate(created.id());

        AccountSnapshot secondReactivation =
            service.reactivate(created.id());

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
            service.close(created.id());

        AccountSnapshot secondClosure =
            service.close(created.id());

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
    void missingCustomerCannotReceiveAccount() {
        UUID missingCustomerId =
            UUID.randomUUID();

        assertThatThrownBy(
            () ->
                service.create(
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
                service.create(
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
                service.create(
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
                service.create(
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
                service.create(
                    UUID.randomUUID()
                )
        )
            .isInstanceOf(
                AccessDeniedException.class
            );

        assertThat(repository.count())
            .isZero();
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