package com.samharrison.payments.reconciliation.internal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.test.context.support.WithMockUser;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

@SpringBootTest
@Testcontainers
@WithMockUser(roles = "RECONCILIATION_ANALYST")
class SettlementDiscrepancyPersistenceIntegrationTest {

    private static final Instant CREATED_AT =
        Instant.parse("2026-07-24T10:00:00Z");

    @Container
    @ServiceConnection
    static final PostgreSQLContainer POSTGRESQL =
        new PostgreSQLContainer(
            "postgres:18.4-alpine"
        )
            .withDatabaseName(
                "settlement_resolution_test"
            )
            .withUsername(
                "settlement_resolution_test"
            )
            .withPassword(
                "settlement_resolution_test_only"
            );

    @Autowired
    private SettlementDiscrepancyResolutionService
        resolutionService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void appliesSettlementResolutionMigration() {
        Long migrationCount =
            jdbcTemplate.queryForObject(
                """
                SELECT COUNT(*)
                FROM public.flyway_schema_history
                WHERE version = '18'
                  AND success = TRUE
                """,
                Long.class
            );

        assertThat(migrationCount).isEqualTo(1L);
    }

    @Test
    void resolvesOnceWithImmutableAttributedEvidence() {
        Fixture fixture = createOpenDiscrepancy();

        resolutionService.resolve(
            fixture.discrepancyId(),
            0L,
            fixture.actorId(),
            SettlementResolutionDecision
                .INTERNAL_CORRECTION_REQUIRED,
            "  Investigate the internal posting.  "
        );

        assertThat(
            jdbcTemplate.queryForObject(
                """
                SELECT status
                FROM settlement_discrepancy
                WHERE id = ?
                """,
                String.class,
                fixture.discrepancyId()
            )
        )
            .isEqualTo("RESOLVED");

        assertThat(
            jdbcTemplate.queryForObject(
                """
                SELECT version
                FROM settlement_discrepancy
                WHERE id = ?
                """,
                Long.class,
                fixture.discrepancyId()
            )
        )
            .isEqualTo(1L);

        assertThat(
            jdbcTemplate.queryForMap(
                """
                SELECT
                    actor_identity_user_id,
                    decision,
                    reason,
                    discrepancy_version
                FROM settlement_resolution
                WHERE settlement_discrepancy_id = ?
                """,
                fixture.discrepancyId()
            )
        )
            .containsEntry(
                "actor_identity_user_id",
                fixture.actorId()
            )
            .containsEntry(
                "decision",
                "INTERNAL_CORRECTION_REQUIRED"
            )
            .containsEntry(
                "reason",
                "Investigate the internal posting."
            )
            .containsEntry(
                "discrepancy_version",
                0L
            );
    }

    @Test
    void staleResolutionFailsBeforeLifecycleConflict() {
        Fixture fixture = createOpenDiscrepancy();

        resolutionService.resolve(
            fixture.discrepancyId(),
            0L,
            fixture.actorId(),
            SettlementResolutionDecision.ACCEPTED,
            "External settlement evidence accepted."
        );

        assertThatThrownBy(
            () ->
                resolutionService.resolve(
                    fixture.discrepancyId(),
                    0L,
                    fixture.actorId(),
                    SettlementResolutionDecision.ACCEPTED,
                    "Attempted stale replay."
                )
        )
            .isInstanceOf(
                SettlementDiscrepancyVersionConflictException
                    .class
            )
            .hasMessageContaining("has version 1");
    }

    @Test
    void matchingResolvedVersionCannotResolveTwice() {
        Fixture fixture = createOpenDiscrepancy();

        resolutionService.resolve(
            fixture.discrepancyId(),
            0L,
            fixture.actorId(),
            SettlementResolutionDecision.ACCEPTED,
            "External settlement evidence accepted."
        );

        assertThatThrownBy(
            () ->
                resolutionService.resolve(
                    fixture.discrepancyId(),
                    1L,
                    fixture.actorId(),
                    SettlementResolutionDecision.ACCEPTED,
                    "Attempted second decision."
                )
        )
            .isInstanceOf(
                SettlementDiscrepancyLifecycleException.class
            );
    }

    @Test
    void databaseRejectsResolutionWithoutTransition() {
        Fixture fixture = createOpenDiscrepancy();

        assertThatThrownBy(
            () ->
                insertResolutionDirectly(
                    fixture,
                    UUID.randomUUID()
                )
        )
            .isInstanceOf(DataAccessException.class)
            .hasMessageContaining(
                "settlement resolution must complete "
            );
    }

    @Test
    void databaseRejectsTransitionWithoutResolution() {
        Fixture fixture = createOpenDiscrepancy();

        assertThatThrownBy(
            () ->
                jdbcTemplate.update(
                    """
                    UPDATE settlement_discrepancy
                    SET
                        status = 'RESOLVED',
                        version = 1
                    WHERE id = ?
                    """,
                    fixture.discrepancyId()
                )
        )
            .isInstanceOf(DataAccessException.class)
            .hasMessageContaining(
                "settlement discrepancy requires "
            );
    }

    @Test
    void databaseRejectsResolutionMutationAndDelete() {
        Fixture fixture = createOpenDiscrepancy();

        resolutionService.resolve(
            fixture.discrepancyId(),
            0L,
            fixture.actorId(),
            SettlementResolutionDecision
                .EXTERNAL_CORRECTION_REQUIRED,
            "External record must be corrected."
        );

        assertThatThrownBy(
            () ->
                jdbcTemplate.update(
                    """
                    UPDATE settlement_resolution
                    SET reason = 'Changed evidence'
                    WHERE settlement_discrepancy_id = ?
                    """,
                    fixture.discrepancyId()
                )
        )
            .isInstanceOf(DataAccessException.class)
            .hasMessageContaining(
                "settlement_resolution is immutable"
            );

        assertThatThrownBy(
            () ->
                jdbcTemplate.update(
                    """
                    DELETE FROM settlement_resolution
                    WHERE settlement_discrepancy_id = ?
                    """,
                    fixture.discrepancyId()
                )
        )
            .isInstanceOf(DataAccessException.class)
            .hasMessageContaining(
                "settlement_resolution is immutable"
            );
    }

    @Test
    void databaseRejectsDiscrepancyEvidenceMutation() {
        Fixture fixture = createOpenDiscrepancy();

        assertThatThrownBy(
            () ->
                jdbcTemplate.update(
                    """
                    UPDATE settlement_discrepancy
                    SET code = 'AMOUNT_MISMATCH'
                    WHERE id = ?
                    """,
                    fixture.discrepancyId()
                )
        )
            .isInstanceOf(DataAccessException.class)
            .hasMessageContaining(
                "settlement discrepancy evidence "
                    + "is immutable"
            );
    }

    private Fixture createOpenDiscrepancy() {
        UUID actorId = insertIdentityUser();
        UUID importId = UUID.randomUUID();
        UUID recordId = UUID.randomUUID();
        UUID resultId = UUID.randomUUID();
        UUID discrepancyId = UUID.randomUUID();
        UUID paymentId = UUID.randomUUID();
        String fingerprint =
            UUID.randomUUID()
                .toString()
                .replace("-", "")
                .repeat(2);

        jdbcTemplate.update(
            """
            INSERT INTO settlement_import (
                id,
                raw_file_sha256,
                raw_file_size_bytes,
                original_filename,
                actor_identity_user_id,
                status,
                row_count,
                matched_count,
                discrepancy_count,
                created_at,
                completed_at,
                version
            )
            VALUES (
                ?, ?, 128, 'resolution-test.csv', ?,
                'PROCESSING', NULL, NULL, NULL,
                ?, NULL, 0
            )
            """,
            importId,
            fingerprint,
            actorId,
            CREATED_AT.atOffset(ZoneOffset.UTC)
        );

        jdbcTemplate.update(
            """
            INSERT INTO settlement_record (
                id,
                settlement_import_id,
                row_number,
                settlement_record_id,
                payment_id,
                amount_minor_units,
                currency,
                settled_at
            )
            VALUES (?, ?, 1, ?, ?, 500, 'GBP', ?)
            """,
            recordId,
            importId,
            "resolution-" + recordId,
            paymentId,
            CREATED_AT.atOffset(ZoneOffset.UTC)
        );

        jdbcTemplate.update(
            """
            INSERT INTO settlement_result (
                id,
                settlement_import_id,
                settlement_record_id,
                row_number,
                outcome,
                discrepancy_code,
                reconciled_at
            )
            VALUES (
                ?, ?, ?, 1, 'DISCREPANCY',
                'PAYMENT_NOT_FOUND', ?
            )
            """,
            resultId,
            importId,
            recordId,
            CREATED_AT.atOffset(ZoneOffset.UTC)
        );

        jdbcTemplate.update(
            """
            INSERT INTO settlement_discrepancy (
                id,
                settlement_import_id,
                settlement_result_id,
                settlement_record_id,
                code,
                status,
                created_at,
                version
            )
            VALUES (
                ?, ?, ?, ?, 'PAYMENT_NOT_FOUND',
                'OPEN', ?, 0
            )
            """,
            discrepancyId,
            importId,
            resultId,
            recordId,
            CREATED_AT.atOffset(ZoneOffset.UTC)
        );

        jdbcTemplate.update(
            """
            UPDATE settlement_import
            SET
                status = 'COMPLETED',
                row_count = 1,
                matched_count = 0,
                discrepancy_count = 1,
                completed_at = ?,
                version = 1
            WHERE id = ?
            """,
            CREATED_AT
                .plusSeconds(1L)
                .atOffset(ZoneOffset.UTC),
            importId
        );

        return new Fixture(
            actorId,
            discrepancyId
        );
    }

    private UUID insertIdentityUser() {
        UUID userId = UUID.randomUUID();
        String email =
            userId + "@settlement-resolution.test";

        jdbcTemplate.update(
            """
            INSERT INTO identity_user (
                id,
                email,
                normalized_email,
                password_hash,
                status,
                failed_login_attempts,
                created_at,
                updated_at,
                version
            )
            VALUES (?, ?, ?, ?, 'ACTIVE', 0, ?, ?, 0)
            """,
            userId,
            email,
            email,
            "settlement-resolution-password-hash",
            CREATED_AT.atOffset(ZoneOffset.UTC),
            CREATED_AT.atOffset(ZoneOffset.UTC)
        );

        return userId;
    }

    private void insertResolutionDirectly(
        Fixture fixture,
        UUID resolutionId
    ) {
        jdbcTemplate.update(
            """
            INSERT INTO settlement_resolution (
                id,
                settlement_discrepancy_id,
                actor_identity_user_id,
                decision,
                reason,
                discrepancy_version,
                decided_at
            )
            VALUES (
                ?, ?, ?, 'ACCEPTED',
                'Direct unresolved evidence.',
                0, ?
            )
            """,
            resolutionId,
            fixture.discrepancyId(),
            fixture.actorId(),
            CREATED_AT
                .plusSeconds(2L)
                .atOffset(ZoneOffset.UTC)
        );
    }

    private record Fixture(
        UUID actorId,
        UUID discrepancyId
    ) {
    }
}
