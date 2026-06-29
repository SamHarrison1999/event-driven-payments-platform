package com.samharrison.payments.account.internal;

import static com.samharrison.payments.account.internal.AccountCurrency.GBP;
import static com.samharrison.payments.account.internal.AccountStatus.ACTIVE;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.samharrison.payments.shared.GbpAmount;
import jakarta.persistence.EntityManager;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

@SpringBootTest
@Testcontainers
@Transactional
class CustomerAccountPersistenceIntegrationTest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer POSTGRESQL =
        new PostgreSQLContainer(
            "postgres:18.4-alpine"
        )
            .withDatabaseName(
                "payments_account_test"
            )
            .withUsername("payments_test")
            .withPassword("payments_test_only");

    @Autowired
    private CustomerAccountRepository repository;

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void appliesAccountSchemaMigration() {
        Long migrationCount =
            jdbcTemplate.queryForObject(
                """
                SELECT COUNT(*)
                FROM public.flyway_schema_history
                WHERE version = '6'
                  AND success = TRUE
                """,
                Long.class
            );

        assertThat(migrationCount)
            .isEqualTo(1L);
    }

    @Test
    void persistsReloadsAndUpdatesAccount() {
        Instant createdAt =
            Instant.parse(
                "2026-06-29T09:00:00Z"
            );

        UUID customerId =
            insertCustomer(createdAt);

        CustomerAccount account =
            CustomerAccount.create(
                customerId,
                createdAt
            );

        repository.saveAndFlush(account);
        entityManager.clear();

        CustomerAccount reloaded =
            repository
                .findById(account.id())
                .orElseThrow();

        assertThat(reloaded.id())
            .isEqualTo(account.id());

        assertThat(reloaded.customerId())
            .isEqualTo(customerId);

        assertThat(reloaded.currency())
            .isEqualTo(GBP);

        assertThat(reloaded.balance())
            .isEqualTo(GbpAmount.ZERO);

        assertThat(reloaded.status())
            .isEqualTo(ACTIVE);

        assertThat(reloaded.createdAt())
            .isEqualTo(createdAt);

        assertThat(reloaded.updatedAt())
            .isEqualTo(createdAt);

        assertThat(reloaded.version())
            .isZero();

        Instant creditedAt =
            createdAt.plusSeconds(60);

        reloaded.credit(
            GbpAmount.ofMinorUnits(2_500L),
            creditedAt
        );

        repository.saveAndFlush(reloaded);
        entityManager.clear();

        CustomerAccount updated =
            repository
                .findById(account.id())
                .orElseThrow();

        assertThat(updated.balance())
            .isEqualTo(
                GbpAmount.ofMinorUnits(2_500L)
            );

        assertThat(updated.updatedAt())
            .isEqualTo(creditedAt);

        assertThat(updated.version())
            .isEqualTo(1L);
    }

    @Test
    void databaseRejectsMissingCustomer() {
        Instant timestamp =
            Instant.parse(
                "2026-06-29T09:00:00Z"
            );

        assertThatThrownBy(
            () ->
                insertAccount(
                    UUID.randomUUID(),
                    "GBP",
                    0L,
                    "ACTIVE",
                    timestamp,
                    timestamp,
                    0L
                )
        )
            .isInstanceOf(
                DataIntegrityViolationException.class
            );
    }

    @Test
    void databaseRejectsNegativeBalance() {
        Instant timestamp =
            Instant.parse(
                "2026-06-29T09:00:00Z"
            );

        UUID customerId =
            insertCustomer(timestamp);

        assertThatThrownBy(
            () ->
                insertAccount(
                    customerId,
                    "GBP",
                    -1L,
                    "ACTIVE",
                    timestamp,
                    timestamp,
                    0L
                )
        )
            .isInstanceOf(
                DataIntegrityViolationException.class
            );
    }

    @Test
    void databaseRejectsUnknownCurrency() {
        Instant timestamp =
            Instant.parse(
                "2026-06-29T09:00:00Z"
            );

        UUID customerId =
            insertCustomer(timestamp);

        assertThatThrownBy(
            () ->
                insertAccount(
                    customerId,
                    "USD",
                    0L,
                    "ACTIVE",
                    timestamp,
                    timestamp,
                    0L
                )
        )
            .isInstanceOf(
                DataIntegrityViolationException.class
            );
    }

    @Test
    void databaseRejectsUnknownStatus() {
        Instant timestamp =
            Instant.parse(
                "2026-06-29T09:00:00Z"
            );

        UUID customerId =
            insertCustomer(timestamp);

        assertThatThrownBy(
            () ->
                insertAccount(
                    customerId,
                    "GBP",
                    0L,
                    "UNKNOWN",
                    timestamp,
                    timestamp,
                    0L
                )
        )
            .isInstanceOf(
                DataIntegrityViolationException.class
            );
    }

    @Test
    void databaseRejectsClosedAccountWithFunds() {
        Instant timestamp =
            Instant.parse(
                "2026-06-29T09:00:00Z"
            );

        UUID customerId =
            insertCustomer(timestamp);

        assertThatThrownBy(
            () ->
                insertAccount(
                    customerId,
                    "GBP",
                    1L,
                    "CLOSED",
                    timestamp,
                    timestamp,
                    0L
                )
        )
            .isInstanceOf(
                DataIntegrityViolationException.class
            );
    }

    @Test
    void databaseRejectsReversedTimestamps() {
        Instant updatedAt =
            Instant.parse(
                "2026-06-29T09:00:00Z"
            );

        Instant createdAt =
            updatedAt.plusSeconds(60);

        UUID customerId =
            insertCustomer(updatedAt);

        assertThatThrownBy(
            () ->
                insertAccount(
                    customerId,
                    "GBP",
                    0L,
                    "ACTIVE",
                    createdAt,
                    updatedAt,
                    0L
                )
        )
            .isInstanceOf(
                DataIntegrityViolationException.class
            );
    }

    @Test
    void databaseRejectsNegativeVersion() {
        Instant timestamp =
            Instant.parse(
                "2026-06-29T09:00:00Z"
            );

        UUID customerId =
            insertCustomer(timestamp);

        assertThatThrownBy(
            () ->
                insertAccount(
                    customerId,
                    "GBP",
                    0L,
                    "ACTIVE",
                    timestamp,
                    timestamp,
                    -1L
                )
        )
            .isInstanceOf(
                DataIntegrityViolationException.class
            );
    }

    private UUID insertCustomer(
        Instant timestamp
    ) {
        UUID customerId =
            UUID.randomUUID();

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
            "Account Test Customer",
            "ACTIVE",
            timestamp.atOffset(ZoneOffset.UTC),
            timestamp.atOffset(ZoneOffset.UTC),
            0L
        );

        return customerId;
    }

    private void insertAccount(
        UUID customerId,
        String currency,
        long balanceMinorUnits,
        String status,
        Instant createdAt,
        Instant updatedAt,
        long version
    ) {
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
            UUID.randomUUID(),
            customerId,
            currency,
            balanceMinorUnits,
            status,
            createdAt.atOffset(ZoneOffset.UTC),
            updatedAt.atOffset(ZoneOffset.UTC),
            version
        );
    }
}