package com.samharrison.payments.identity.internal;

import static com.samharrison.payments.identity.internal.IdentityRole.CUSTOMER;
import static com.samharrison.payments.identity.internal.IdentityUserStatus.ACTIVE;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import jakarta.persistence.EntityManager;
import java.time.Instant;
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
class IdentityPersistenceIntegrationTest {

    private static final String TEST_PASSWORD_HASH =
        "{test}not-a-real-password-hash";

    @Container
    @ServiceConnection
    static final PostgreSQLContainer POSTGRESQL =
        new PostgreSQLContainer(
            "postgres:18.4-alpine"
        )
            .withDatabaseName(
                "payments_identity_test"
            )
            .withUsername("payments_test")
            .withPassword("payments_test_only");

    @Autowired
    private IdentityUserRepository repository;

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void appliesTheIdentitySchemaMigration() {
        Long migrationCount =
            jdbcTemplate.queryForObject(
                """
                SELECT COUNT(*)
                FROM public.flyway_schema_history
                WHERE version = '2'
                  AND success = TRUE
                """,
                Long.class
            );

        assertThat(migrationCount)
            .isEqualTo(1L);
    }

    @Test
    void persistsAndReloadsACustomerIdentity() {
        Instant registeredAt = Instant.parse(
            "2026-06-24T18:00:00Z"
        );

        IdentityUser user =
            IdentityUser.registerCustomer(
                EmailAddress.of(
                    "Sam.Example@Example.COM"
                ),
                TEST_PASSWORD_HASH,
                registeredAt
            );

        repository.saveAndFlush(user);
        entityManager.clear();

        IdentityUser reloaded = repository
            .findByNormalizedEmail(
                "sam.example@example.com"
            )
            .orElseThrow();

        assertThat(reloaded.id())
            .isEqualTo(user.id());

        assertThat(reloaded.email())
            .isEqualTo(
                "Sam.Example@Example.COM"
            );

        assertThat(reloaded.normalizedEmail())
            .isEqualTo(
                "sam.example@example.com"
            );

        assertThat(reloaded.status())
            .isEqualTo(ACTIVE);

        assertThat(reloaded.failedLoginAttempts())
            .isZero();

        assertThat(reloaded.lockedUntil())
            .isNull();

        assertThat(reloaded.createdAt())
            .isEqualTo(registeredAt);

        assertThat(reloaded.updatedAt())
            .isEqualTo(registeredAt);

        assertThat(reloaded.roles())
            .containsExactly(CUSTOMER);
    }

    @Test
    void rejectsDuplicateNormalizedEmailAddresses() {
        Instant registeredAt = Instant.parse(
            "2026-06-24T18:00:00Z"
        );

        repository.saveAndFlush(
            IdentityUser.registerCustomer(
                EmailAddress.of(
                    "Sam.Example@Example.COM"
                ),
                TEST_PASSWORD_HASH,
                registeredAt
            )
        );

        IdentityUser duplicate =
            IdentityUser.registerCustomer(
                EmailAddress.of(
                    "sam.example@example.com"
                ),
                TEST_PASSWORD_HASH,
                registeredAt
            );

        assertThatThrownBy(
            () -> repository.saveAndFlush(
                duplicate
            )
        )
            .isInstanceOf(
                DataIntegrityViolationException.class
            );
    }
}
