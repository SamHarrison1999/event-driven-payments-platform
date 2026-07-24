package com.samharrison.payments.reconciliation.internal;

import java.sql.Timestamp;
import java.util.Objects;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
class SettlementImportReservationStore {

    private static final String RESERVE_SQL = """
        INSERT INTO settlement_import (
            id,
            raw_file_sha256,
            raw_file_size_bytes,
            original_filename,
            actor_identity_user_id,
            status,
            created_at,
            version
        )
        VALUES (?, ?, ?, ?, ?, 'PROCESSING', ?, 0)
        ON CONFLICT (raw_file_sha256) DO NOTHING
        """;

    private final JdbcTemplate jdbcTemplate;

    private final SettlementImportRepository repository;

    SettlementImportReservationStore(
        JdbcTemplate jdbcTemplate,
        SettlementImportRepository repository
    ) {
        this.jdbcTemplate =
            Objects.requireNonNull(
                jdbcTemplate,
                "jdbcTemplate must not be null"
            );
        this.repository =
            Objects.requireNonNull(
                repository,
                "repository must not be null"
            );
    }

    SettlementImportReservation reserve(
        SettlementImport candidate
    ) {
        SettlementImport requiredCandidate =
            Objects.requireNonNull(
                candidate,
                "candidate must not be null"
            );

        int inserted =
            jdbcTemplate.update(
                RESERVE_SQL,
                requiredCandidate.id(),
                requiredCandidate.rawFileSha256(),
                requiredCandidate.rawFileSizeBytes(),
                requiredCandidate.originalFilename(),
                requiredCandidate.actorIdentityUserId(),
                Timestamp.from(
                    requiredCandidate.createdAt()
                )
            );

        if (inserted == 1) {
            SettlementImport stored =
                repository
                    .findById(requiredCandidate.id())
                    .orElseThrow(
                        () ->
                            new IllegalStateException(
                                "Reserved settlement import "
                                    + "could not be read."
                            )
                    );

            return new SettlementImportReservation(
                stored,
                false
            );
        }

        SettlementImport existing =
            repository
                .findByRawFileSha256(
                    requiredCandidate.rawFileSha256()
                )
                .filter(
                    candidateImport ->
                        candidateImport.status()
                            == SettlementImportStatus
                                .COMPLETED
                )
                .orElseThrow(
                    () ->
                        new IllegalStateException(
                            "A fingerprint reservation did "
                                + "not resolve to a completed "
                                + "settlement import."
                        )
                );

        return new SettlementImportReservation(
            existing,
            true
        );
    }
}
