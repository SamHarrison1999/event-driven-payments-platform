package com.samharrison.payments.customer.internal;

import static com.samharrison.payments.customer.CustomerAccountEligibilityException.Reason.NOT_ACTIVE;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.samharrison.payments.customer.CustomerAccountEligibilityException;
import com.samharrison.payments.customer.CustomerOwnership;
import com.samharrison.payments.customer.CustomerOwnershipNotFoundException;
import com.samharrison.payments.identity.IdentityUserNotFoundException;
import java.time.Instant;
import java.time.ZoneOffset;
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
import org.springframework.jdbc.core.JdbcTemplate;

@SpringBootTest
@Testcontainers
@Transactional
@DirtiesContext(
    classMode = DirtiesContext.ClassMode.AFTER_CLASS
)
class CustomerOwnershipManagementServiceIntegrationTest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer POSTGRESQL =
        new PostgreSQLContainer(
            "postgres:18.4-alpine"
        )
            .withDatabaseName(
                "payments_customer_ownership_test"
            )
            .withUsername("payments_test")
            .withPassword("payments_test_only");

    @Autowired
    private CustomerOwnershipManagementService
        managementService;

    @Autowired
    private CustomerOwnership ownership;

    @Autowired
    private CustomerIdentityAssignmentRepository
        repository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    @WithMockUser(roles = "OPERATIONS")
    void operationsUserAssignsIdentityToCustomer() {
        UUID identityUserId =
            insertIdentityUser();

        UUID customerId =
            insertCustomer("ACTIVE");

        CustomerOwnershipSnapshot assigned =
            managementService.assign(
                identityUserId,
                customerId
            );

        assertThat(assigned.identityUserId())
            .isEqualTo(identityUserId);

        assertThat(assigned.customerId())
            .isEqualTo(customerId);

        assertThat(assigned.assignedAt())
            .isNotNull();

        assertThat(assigned.version())
            .isZero();

        assertThat(
            ownership.requireCustomerId(
                identityUserId
            )
        )
            .isEqualTo(customerId);
    }

    @Test
    @WithMockUser(roles = "OPERATIONS")
    void assignedIdentityIsFoundWithoutThrowing() {
        UUID identityUserId =
            insertIdentityUser();

        UUID customerId =
            insertCustomer("ACTIVE");

        managementService.assign(
            identityUserId,
            customerId
        );

        assertThat(
            ownership.findCustomerId(
                identityUserId
            )
        )
            .contains(customerId);
    }

    @Test
    void unassignedIdentityReturnsEmptyLookup() {
        UUID identityUserId =
            insertIdentityUser();

        assertThat(
            ownership.findCustomerId(
                identityUserId
            )
        )
            .isEmpty();
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void administratorAssignsIdentityToCustomer() {
        UUID identityUserId =
            insertIdentityUser();

        UUID customerId =
            insertCustomer("ACTIVE");

        CustomerOwnershipSnapshot assigned =
            managementService.assign(
                identityUserId,
                customerId
            );

        assertThat(assigned.customerId())
            .isEqualTo(customerId);
    }

    @Test
    @WithMockUser(roles = "OPERATIONS")
    void repeatedAssignmentIsIdempotent() {
        UUID identityUserId =
            insertIdentityUser();

        UUID customerId =
            insertCustomer("ACTIVE");

        CustomerOwnershipSnapshot first =
            managementService.assign(
                identityUserId,
                customerId
            );

        CustomerOwnershipSnapshot second =
            managementService.assign(
                identityUserId,
                customerId
            );

        assertThat(second)
            .isEqualTo(first);

        assertThat(repository.count())
            .isEqualTo(1L);
    }

    @Test
    @WithMockUser(roles = "OPERATIONS")
    void identityCannotBeAssignedToTwoCustomers() {
        UUID identityUserId =
            insertIdentityUser();

        UUID firstCustomerId =
            insertCustomer("ACTIVE");

        UUID secondCustomerId =
            insertCustomer("ACTIVE");

        managementService.assign(
            identityUserId,
            firstCustomerId
        );

        assertThatThrownBy(
            () ->
                managementService.assign(
                    identityUserId,
                    secondCustomerId
                )
        )
            .isInstanceOf(
                CustomerOwnershipConflictException.class
            )
            .hasMessageContaining(
                identityUserId.toString(),
                firstCustomerId.toString(),
                secondCustomerId.toString()
            );

        assertThat(
            ownership.requireCustomerId(
                identityUserId
            )
        )
            .isEqualTo(firstCustomerId);
    }

    @Test
    @WithMockUser(roles = "OPERATIONS")
    void missingIdentityCannotBeAssigned() {
        UUID missingIdentityUserId =
            UUID.randomUUID();

        UUID customerId =
            insertCustomer("ACTIVE");

        assertThatThrownBy(
            () ->
                managementService.assign(
                    missingIdentityUserId,
                    customerId
                )
        )
            .isInstanceOf(
                IdentityUserNotFoundException.class
            )
            .hasMessageContaining(
                missingIdentityUserId.toString()
            );

        assertThat(repository.count())
            .isZero();
    }

    @Test
    @WithMockUser(roles = "OPERATIONS")
    void inactiveCustomerCannotReceiveAssignment() {
        UUID identityUserId =
            insertIdentityUser();

        UUID customerId =
            insertCustomer("SUSPENDED");

        assertThatThrownBy(
            () ->
                managementService.assign(
                    identityUserId,
                    customerId
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
    void missingOwnershipIsReported() {
        UUID identityUserId =
            insertIdentityUser();

        assertThatThrownBy(
            () ->
                ownership.requireCustomerId(
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
    @WithMockUser(roles = "CUSTOMER")
    void customerUserCannotAssignOwnership() {
        assertThatThrownBy(
            () ->
                managementService.assign(
                    UUID.randomUUID(),
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
    void anonymousUserCannotAssignOwnership() {
        assertThatThrownBy(
            () ->
                managementService.assign(
                    UUID.randomUUID(),
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
    void appliesOwnershipMigration() {
        Long migrationCount =
            jdbcTemplate.queryForObject(
                """
                SELECT COUNT(*)
                FROM public.flyway_schema_history
                WHERE version = '7'
                  AND success = TRUE
                """,
                Long.class
            );

        assertThat(migrationCount)
            .isEqualTo(1L);
    }

    private UUID insertIdentityUser() {
        UUID identityUserId =
            UUID.randomUUID();

        Instant timestamp =
            Instant.parse(
                "2026-06-29T09:00:00Z"
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
            "Ownership Test Customer",
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
