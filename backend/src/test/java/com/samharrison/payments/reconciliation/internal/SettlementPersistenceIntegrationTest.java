package com.samharrison.payments.reconciliation.internal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import jakarta.persistence.EntityManager;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

@SpringBootTest
@Testcontainers
@Transactional
class SettlementPersistenceIntegrationTest {

    private static final Instant CREATED_AT =
        Instant.parse("2026-07-24T10:00:00Z");

    @Container
    @ServiceConnection
    static final PostgreSQLContainer POSTGRESQL =
        new PostgreSQLContainer(
            "postgres:18.4-alpine"
        )
            .withDatabaseName("reconciliation_test")
            .withUsername("reconciliation_test")
            .withPassword(
                "reconciliation_test_only"
            );

    @Autowired
    private SettlementImportRepository importRepository;

    @Autowired
    private ImportedSettlementRecordRepository
        recordRepository;

    @Autowired
    private SettlementCsvParser parser;

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void appliesSettlementImportFoundationMigration() {
        Long migrationCount =
            jdbcTemplate.queryForObject(
                """
                SELECT COUNT(*)
                FROM public.flyway_schema_history
                WHERE version = '16'
                  AND success = TRUE
                """,
                Long.class
            );

        assertThat(migrationCount)
            .isEqualTo(1L);
    }

    @Test
    void persistsRowsInOriginalOrderAndCompletesImport() {
        UUID actorId = insertIdentityUser();
        ParsedSettlementFile parsed =
            parser.parse(
                validCsv(
                    "record-1",
                    "record-2"
                )
            );
        SettlementImport settlementImport =
            SettlementImport.processing(
                parsed,
                "daily-settlement.csv",
                actorId,
                CREATED_AT
            );

        importRepository.saveAndFlush(
            settlementImport
        );

        List<ImportedSettlementRecord> records =
            parsed.records()
                .stream()
                .map(
                    source ->
                        ImportedSettlementRecord.from(
                            settlementImport,
                            source
                        )
                )
                .toList();

        recordRepository.saveAllAndFlush(records);

        settlementImport.complete(
            2,
            1,
            1,
            CREATED_AT.plusSeconds(1L)
        );
        importRepository.saveAndFlush(
            settlementImport
        );
        entityManager.clear();

        SettlementImport reloaded =
            importRepository
                .findByRawFileSha256(
                    parsed.rawFileSha256()
                )
                .orElseThrow();

        assertThat(reloaded.status())
            .isEqualTo(
                SettlementImportStatus.COMPLETED
            );
        assertThat(reloaded.rowCount())
            .isEqualTo(2);
        assertThat(reloaded.matchedCount())
            .isEqualTo(1);
        assertThat(reloaded.discrepancyCount())
            .isEqualTo(1);
        assertThat(reloaded.version())
            .isEqualTo(1L);

        assertThat(
            recordRepository
                .findAllBySettlementImportIdOrderByRowNumber(
                    settlementImport.id()
                )
        )
            .extracting(
                ImportedSettlementRecord
                    ::settlementRecordId
            )
            .containsExactly(
                "record-1",
                "record-2"
            );
    }

    @Test
    void databaseRejectsDuplicateRawFileFingerprint() {
        UUID actorId = insertIdentityUser();
        ParsedSettlementFile parsed =
            parser.parse(
                validCsv("record-1")
            );
        SettlementImport first =
            SettlementImport.processing(
                parsed,
                "first.csv",
                actorId,
                CREATED_AT
            );
        SettlementImport duplicate =
            SettlementImport.processing(
                parsed,
                "second.csv",
                actorId,
                CREATED_AT
            );

        importRepository.saveAndFlush(first);

        assertThatThrownBy(
            () ->
                importRepository.saveAndFlush(
                    duplicate
                )
        )
            .isInstanceOf(
                DataIntegrityViolationException.class
            );
    }

    @Test
    void databaseRejectsExternalIdentifierAcrossImports() {
        UUID actorId = insertIdentityUser();
        SettlementImport first =
            processingImport(
                actorId,
                "record-shared",
                "first.csv"
            );
        SettlementImport second =
            processingImport(
                actorId,
                "record-shared",
                "second.csv"
            );

        importRepository.saveAndFlush(first);
        importRepository.saveAndFlush(second);

        recordRepository.saveAndFlush(
            ImportedSettlementRecord.from(
                first,
                parsedRecord("record-shared")
            )
        );

        assertThatThrownBy(
            () ->
                recordRepository.saveAndFlush(
                    ImportedSettlementRecord.from(
                        second,
                        parsedRecord(
                            "record-shared"
                        )
                    )
                )
        )
            .isInstanceOf(
                DataIntegrityViolationException.class
            );
    }

    @Test
    void databaseRejectsSettlementRecordUpdates() {
        PersistedRecord persisted =
            persistOneRecord();

        assertThatThrownBy(
            () ->
                jdbcTemplate.update(
                    """
                    UPDATE settlement_record
                    SET amount_minor_units = ?
                    WHERE id = ?
                    """,
                    999L,
                    persisted.recordId()
                )
        )
            .isInstanceOf(
                DataAccessException.class
            )
            .hasStackTraceContaining(
                "settlement_record is immutable"
            );
    }

    @Test
    void databaseRejectsSettlementRecordDeletes() {
        PersistedRecord persisted =
            persistOneRecord();

        assertThatThrownBy(
            () ->
                jdbcTemplate.update(
                    """
                    DELETE FROM settlement_record
                    WHERE id = ?
                    """,
                    persisted.recordId()
                )
        )
            .isInstanceOf(
                DataAccessException.class
            )
            .hasStackTraceContaining(
                "settlement_record is immutable"
            );
    }

    @Test
    void databaseRejectsCompletedImportMutation() {
        PersistedRecord persisted =
            persistOneRecord();
        SettlementImport settlementImport =
            importRepository
                .findById(persisted.importId())
                .orElseThrow();

        settlementImport.complete(
            1,
            1,
            0,
            CREATED_AT.plusSeconds(1L)
        );
        importRepository.saveAndFlush(
            settlementImport
        );

        assertThatThrownBy(
            () ->
                jdbcTemplate.update(
                    """
                    UPDATE settlement_import
                    SET original_filename = ?
                    WHERE id = ?
                    """,
                    "changed.csv",
                    settlementImport.id()
                )
        )
            .isInstanceOf(
                DataAccessException.class
            )
            .hasStackTraceContaining(
                "completed settlement_import is immutable"
            );
    }

    @Test
    void databaseRejectsInvalidCurrencyAndRowNumber() {
        UUID actorId = insertIdentityUser();
        SettlementImport settlementImport =
            processingImport(
                actorId,
                "record-invalid",
                "invalid.csv"
            );
        importRepository.saveAndFlush(
            settlementImport
        );

        assertThatThrownBy(
            () ->
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
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                    """,
                    UUID.randomUUID(),
                    settlementImport.id(),
                    0,
                    "record-invalid",
                    UUID.randomUUID(),
                    100L,
                    "USD",
                    CREATED_AT.atOffset(
                        ZoneOffset.UTC
                    )
                )
        )
            .isInstanceOf(
                DataIntegrityViolationException.class
            );
    }

    private SettlementImport processingImport(
        UUID actorId,
        String externalIdentifier,
        String filename
    ) {
        String uniqueRecord =
            externalIdentifier
                + "-"
                + UUID.randomUUID();

        return SettlementImport.processing(
            parser.parse(
                validCsv(uniqueRecord)
            ),
            filename,
            actorId,
            CREATED_AT
        );
    }

    private PersistedRecord persistOneRecord() {
        UUID actorId = insertIdentityUser();
        ParsedSettlementFile parsed =
            parser.parse(
                validCsv("record-immutable")
            );
        SettlementImport settlementImport =
            SettlementImport.processing(
                parsed,
                "immutable.csv",
                actorId,
                CREATED_AT
            );
        importRepository.saveAndFlush(
            settlementImport
        );

        ImportedSettlementRecord record =
            ImportedSettlementRecord.from(
                settlementImport,
                parsed.records().getFirst()
            );
        recordRepository.saveAndFlush(record);

        return new PersistedRecord(
            settlementImport.id(),
            record.id()
        );
    }

    private UUID insertIdentityUser() {
        UUID identityUserId = UUID.randomUUID();
        String email =
            identityUserId
                + "@reconciliation.test";

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
            "reconciliation-test-password-hash",
            "ACTIVE",
            0,
            null,
            CREATED_AT.atOffset(ZoneOffset.UTC),
            CREATED_AT.atOffset(ZoneOffset.UTC),
            0L
        );

        return identityUserId;
    }

    private static ParsedSettlementRecord parsedRecord(
        String externalIdentifier
    ) {
        return new ParsedSettlementRecord(
            1,
            externalIdentifier,
            UUID.randomUUID(),
            100L,
            "GBP",
            CREATED_AT
        );
    }

    private static byte[] validCsv(
        String... externalIdentifiers
    ) {
        StringBuilder content =
            new StringBuilder(
                "settlement_record_id,payment_id,"
                    + "amount_minor_units,currency,"
                    + "settled_at"
            );

        for (
            String externalIdentifier
                : externalIdentifiers
        ) {
            content
                .append('\n')
                .append(externalIdentifier)
                .append(',')
                .append(UUID.randomUUID())
                .append(
                    ",100,GBP,"
                        + "2026-07-24T10:00:00Z"
                );
        }

        return content
            .toString()
            .getBytes(StandardCharsets.UTF_8);
    }

    private record PersistedRecord(
        UUID importId,
        UUID recordId
    ) {
    }
}
