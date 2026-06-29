package com.samharrison.payments.customer.internal;

import static com.samharrison.payments.customer.internal.CustomerStatus.ACTIVE;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

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
class CustomerPersistenceIntegrationTest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer POSTGRESQL =
        new PostgreSQLContainer(
            "postgres:18.4-alpine"
        )
            .withDatabaseName(
                "payments_customer_test"
            )
            .withUsername("payments_test")
            .withPassword("payments_test_only");

    @Autowired
    private CustomerProfileRepository repository;

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void appliesCustomerSchemaMigration() {
        Long migrationCount =
            jdbcTemplate.queryForObject(
                """
                SELECT COUNT(*)
                FROM public.flyway_schema_history
                WHERE version = '5'
                  AND success = TRUE
                """,
                Long.class
            );

        assertThat(migrationCount)
            .isEqualTo(1L);
    }

    @Test
    void persistsAndReloadsCustomerProfile() {
        Instant createdAt =
            Instant.parse(
                "2026-06-26T09:00:00Z"
            );

        CustomerProfile customer =
            CustomerProfile.create(
                CustomerName.of(
                    "Sam Example"
                ),
                createdAt
            );

        repository.saveAndFlush(customer);
        entityManager.clear();

        CustomerProfile reloaded =
            repository
                .findById(customer.id())
                .orElseThrow();

        assertThat(reloaded.id())
            .isEqualTo(customer.id());

        assertThat(reloaded.fullName())
            .isEqualTo("Sam Example");

        assertThat(reloaded.status())
            .isEqualTo(ACTIVE);

        assertThat(reloaded.createdAt())
            .isEqualTo(createdAt);

        assertThat(reloaded.updatedAt())
            .isEqualTo(createdAt);

        assertThat(reloaded.version())
            .isZero();
    }

    @Test
    void databaseRejectsUntrimmedName() {
        Instant timestamp =
            Instant.parse(
                "2026-06-26T09:00:00Z"
            );

        assertThatThrownBy(
            () ->
                insertCustomer(
                    " Sam Example ",
                    "ACTIVE",
                    timestamp
                )
        )
            .isInstanceOf(
                DataIntegrityViolationException.class
            );
    }

    @Test
    void databaseRejectsControlCharacters() {
        Instant timestamp =
            Instant.parse(
                "2026-06-26T09:00:00Z"
            );

        assertThatThrownBy(
            () ->
                insertCustomer(
                    "Sam\nExample",
                    "ACTIVE",
                    timestamp
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
                "2026-06-26T09:00:00Z"
            );

        assertThatThrownBy(
            () ->
                insertCustomer(
                    "Sam Example",
                    "UNKNOWN",
                    timestamp
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
                "2026-06-26T09:00:00Z"
            );

        Instant createdAt =
            updatedAt.plusSeconds(60);

        assertThatThrownBy(
            () ->
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
                    UUID.randomUUID(),
                    "Sam Example",
                    "ACTIVE",
                    createdAt.atOffset(
                        ZoneOffset.UTC
                    ),
                    updatedAt.atOffset(
                        ZoneOffset.UTC
                    ),
                    0L
                )
        )
            .isInstanceOf(
                DataIntegrityViolationException.class
            );
    }
    @Test
    void appliesPhaseThreeValidationMigration() {
        Long migrationCount =
            jdbcTemplate.queryForObject(
                """
                SELECT COUNT(*)
                FROM public.flyway_schema_history
                WHERE version = '8'
                  AND success = TRUE
                """,
                Long.class
            );

        assertThat(migrationCount)
            .isEqualTo(1L);
    }

    @Test
    void databaseRejectsNegativeCustomerVersion() {
        Instant timestamp =
            Instant.parse(
                "2026-06-29T09:00:00Z"
            );

        assertThatThrownBy(
            () ->
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
                    UUID.randomUUID(),
                    "Invalid Version Customer",
                    "ACTIVE",
                    timestamp.atOffset(
                        ZoneOffset.UTC
                    ),
                    timestamp.atOffset(
                        ZoneOffset.UTC
                    ),
                    -1L
                )
        )
            .isInstanceOf(
                DataIntegrityViolationException.class
            );
    }
    private void insertCustomer(
        String fullName,
        String status,
        Instant timestamp
    ) {
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
            UUID.randomUUID(),
            fullName,
            status,
            timestamp.atOffset(ZoneOffset.UTC),
            timestamp.atOffset(ZoneOffset.UTC),
            0L
        );
    }
}