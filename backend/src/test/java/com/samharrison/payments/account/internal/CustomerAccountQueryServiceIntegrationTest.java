package com.samharrison.payments.account.internal;

import static com.samharrison.payments.account.internal.AccountCurrency.GBP;
import static com.samharrison.payments.account.internal.AccountStatus.ACTIVE;
import static com.samharrison.payments.account.internal.AccountStatus.FROZEN;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.samharrison.payments.customer.CustomerOwnershipNotFoundException;
import com.samharrison.payments.shared.GbpAmount;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
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
class CustomerAccountQueryServiceIntegrationTest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer POSTGRESQL =
        new PostgreSQLContainer(
            "postgres:18.4-alpine"
        )
            .withDatabaseName(
                "payments_customer_account_query_test"
            )
            .withUsername("payments_test")
            .withPassword("payments_test_only");

    @Autowired
    private CustomerAccountQueryService service;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    @WithMockUser(roles = "CUSTOMER")
    void customerUserFindsOnlyOwnedAccountsInOrder() {
        UUID identityUserId =
            insertIdentityUser();

        UUID ownedCustomerId =
            insertCustomer("Owned Customer");

        UUID otherCustomerId =
            insertCustomer("Other Customer");

        insertOwnership(
            identityUserId,
            ownedCustomerId
        );

        UUID firstAccountId =
            insertAccount(
                ownedCustomerId,
                1250L,
                "ACTIVE",
                "2026-06-29T09:00:00Z"
            );

        UUID secondAccountId =
            insertAccount(
                ownedCustomerId,
                5000L,
                "FROZEN",
                "2026-06-29T10:00:00Z"
            );

        insertAccount(
            otherCustomerId,
            9999L,
            "ACTIVE",
            "2026-06-29T08:00:00Z"
        );

        List<AccountSnapshot> accounts =
            service.findOwnedByIdentityUser(
                identityUserId
            );

        assertThat(accounts)
            .extracting(AccountSnapshot::id)
            .containsExactly(
                firstAccountId,
                secondAccountId
            );

        assertThat(accounts.get(0).customerId())
            .isEqualTo(ownedCustomerId);

        assertThat(accounts.get(0).currency())
            .isEqualTo(GBP);

        assertThat(accounts.get(0).balance())
            .isEqualTo(
                GbpAmount.ofMinorUnits(1250L)
            );

        assertThat(accounts.get(0).status())
            .isEqualTo(ACTIVE);

        assertThat(accounts.get(1).status())
            .isEqualTo(FROZEN);
    }

    @Test
    @WithMockUser(roles = "CUSTOMER")
    void customerUserWithNoAccountsGetsEmptyList() {
        UUID identityUserId =
            insertIdentityUser();

        UUID customerId =
            insertCustomer("Empty Customer");

        insertOwnership(
            identityUserId,
            customerId
        );

        assertThat(
            service.findOwnedByIdentityUser(
                identityUserId
            )
        )
            .isEmpty();
    }

    @Test
    @WithMockUser(roles = "CUSTOMER")
    void missingOwnershipIsReported() {
        UUID identityUserId =
            insertIdentityUser();

        assertThatThrownBy(
            () ->
                service.findOwnedByIdentityUser(
                    identityUserId
                )
        )
            .isInstanceOf(
                CustomerOwnershipNotFoundException.class
            )
            .hasMessageContaining(
                identityUserId.toString()
            );
    }

    @Test
    @WithMockUser(roles = "OPERATIONS")
    void operationsUserFindsAccountsByCustomer() {
        UUID customerId =
            insertCustomer("Operations Customer");

        UUID accountId =
            insertAccount(
                customerId,
                300L,
                "ACTIVE",
                "2026-06-29T09:00:00Z"
            );

        assertThat(
            service.findByCustomerId(
                customerId
            )
        )
            .extracting(AccountSnapshot::id)
            .containsExactly(accountId);
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void administratorFindsAccountsByCustomer() {
        UUID customerId =
            insertCustomer("Administrator Customer");

        assertThat(
            service.findByCustomerId(
                customerId
            )
        )
            .isEmpty();
    }

    @Test
    @WithMockUser(roles = "CUSTOMER")
    void customerUserCannotQueryArbitraryCustomer() {
        assertThatThrownBy(
            () ->
                service.findByCustomerId(
                    UUID.randomUUID()
                )
        )
            .isInstanceOf(
                AccessDeniedException.class
            );
    }

    @Test
    @WithMockUser(roles = "OPERATIONS")
    void operationsUserCannotUseCustomerOwnershipQuery() {
        assertThatThrownBy(
            () ->
                service.findOwnedByIdentityUser(
                    UUID.randomUUID()
                )
        )
            .isInstanceOf(
                AccessDeniedException.class
            );
    }

    @Test
    @WithAnonymousUser
    void anonymousUserCannotQueryAccounts() {
        assertThatThrownBy(
            () ->
                service.findOwnedByIdentityUser(
                    UUID.randomUUID()
                )
        )
            .isInstanceOf(
                AccessDeniedException.class
            );

        assertThatThrownBy(
            () ->
                service.findByCustomerId(
                    UUID.randomUUID()
                )
        )
            .isInstanceOf(
                AccessDeniedException.class
            );
    }

    private UUID insertIdentityUser() {
        UUID identityUserId =
            UUID.randomUUID();

        Instant timestamp =
            Instant.parse(
                "2026-06-29T08:00:00Z"
            );

        String email =
            identityUserId
                + "@example.com";

        jdbcTemplate.update(
            """
            INSERT INTO identity_user (
                id,
                email,
                normalized_email,
                password_hash,
                status,
                failed_login_attempts,
                locked_until,
                created_at,
                updated_at,
                version
            )
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            """,
            identityUserId,
            email,
            email,
            "{noop}not-used-in-this-test",
            "ACTIVE",
            0,
            null,
            timestamp.atOffset(
                ZoneOffset.UTC
            ),
            timestamp.atOffset(
                ZoneOffset.UTC
            ),
            0L
        );

        jdbcTemplate.update(
            """
            INSERT INTO identity_user_role (
                user_id,
                role_code
            )
            VALUES (?, ?)
            """,
            identityUserId,
            "CUSTOMER"
        );

        return identityUserId;
    }

    private UUID insertCustomer(
        String fullName
    ) {
        UUID customerId =
            UUID.randomUUID();

        Instant timestamp =
            Instant.parse(
                "2026-06-29T08:00:00Z"
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
            fullName,
            "ACTIVE",
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

    private void insertOwnership(
        UUID identityUserId,
        UUID customerId
    ) {
        Instant timestamp =
            Instant.parse(
                "2026-06-29T08:30:00Z"
            );

        jdbcTemplate.update(
            """
            INSERT INTO customer_identity_assignment (
                identity_user_id,
                customer_id,
                assigned_at,
                version
            )
            VALUES (?, ?, ?, ?)
            """,
            identityUserId,
            customerId,
            timestamp.atOffset(
                ZoneOffset.UTC
            ),
            0L
        );
    }

    private UUID insertAccount(
        UUID customerId,
        long balanceMinorUnits,
        String status,
        String createdAt
    ) {
        UUID accountId =
            UUID.randomUUID();

        Instant timestamp =
            Instant.parse(createdAt);

        jdbcTemplate.update(
            """
            INSERT INTO customer_account (
                id,
                customer_id,
                currency,
                balance_minor_units,
                status,
                created_at,
                updated_at,
                version
            )
            VALUES (?, ?, ?, ?, ?, ?, ?, ?)
            """,
            accountId,
            customerId,
            "GBP",
            balanceMinorUnits,
            status,
            timestamp.atOffset(
                ZoneOffset.UTC
            ),
            timestamp.atOffset(
                ZoneOffset.UTC
            ),
            0L
        );

        return accountId;
    }
}