package com.samharrison.payments.reconciliation.internal;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import java.util.regex.Pattern;

@Entity
@Table(
    name = "settlement_import",
    uniqueConstraints = {
        @UniqueConstraint(
            name =
                "uq_settlement_import_fingerprint",
            columnNames = "raw_file_sha256"
        )
    },
    indexes = {
        @Index(
            name = "idx_settlement_import_created",
            columnList = "created_at,id"
        )
    }
)
class SettlementImport {

    static final int MAX_FILENAME_LENGTH = 255;

    private static final Pattern SHA_256 =
        Pattern.compile("[0-9a-f]{64}");

    @Id
    @Column(
        name = "id",
        nullable = false,
        updatable = false
    )
    private UUID id;

    @Column(
        name = "raw_file_sha256",
        nullable = false,
        updatable = false,
        length = 64
    )
    private String rawFileSha256;

    @Column(
        name = "raw_file_size_bytes",
        nullable = false,
        updatable = false
    )
    private int rawFileSizeBytes;

    @Column(
        name = "original_filename",
        nullable = false,
        updatable = false,
        length = MAX_FILENAME_LENGTH
    )
    private String originalFilename;

    @Column(
        name = "actor_identity_user_id",
        nullable = false,
        updatable = false
    )
    private UUID actorIdentityUserId;

    @Enumerated(EnumType.STRING)
    @Column(
        name = "status",
        nullable = false,
        length = 32
    )
    private SettlementImportStatus status;

    @Column(name = "row_count")
    private Integer rowCount;

    @Column(name = "matched_count")
    private Integer matchedCount;

    @Column(name = "discrepancy_count")
    private Integer discrepancyCount;

    @Column(
        name = "created_at",
        nullable = false,
        updatable = false
    )
    private Instant createdAt;

    @Column(name = "completed_at")
    private Instant completedAt;

    @Version
    @Column(
        name = "version",
        nullable = false
    )
    private long version;

    protected SettlementImport() {
        // Required by JPA.
    }

    private SettlementImport(
        UUID id,
        String rawFileSha256,
        int rawFileSizeBytes,
        String originalFilename,
        UUID actorIdentityUserId,
        Instant createdAt
    ) {
        this.id =
            Objects.requireNonNull(
                id,
                "id must not be null"
            );
        this.rawFileSha256 =
            requireFingerprint(rawFileSha256);

        if (
            rawFileSizeBytes <= 0
                || rawFileSizeBytes
                    > SettlementCsvParser
                        .MAX_FILE_SIZE_BYTES
        ) {
            throw new InvalidSettlementImportException(
                "rawFileSizeBytes must be between "
                    + "1 and 1,048,576"
            );
        }

        this.rawFileSizeBytes = rawFileSizeBytes;
        this.originalFilename =
            requireFilename(originalFilename);
        this.actorIdentityUserId =
            Objects.requireNonNull(
                actorIdentityUserId,
                "actorIdentityUserId must not be null"
            );
        this.createdAt =
            Objects.requireNonNull(
                createdAt,
                "createdAt must not be null"
            );
        status = SettlementImportStatus.PROCESSING;
    }

    static SettlementImport processing(
        ParsedSettlementFile parsedFile,
        String originalFilename,
        UUID actorIdentityUserId,
        Instant createdAt
    ) {
        ParsedSettlementFile requiredFile =
            Objects.requireNonNull(
                parsedFile,
                "parsedFile must not be null"
            );

        return new SettlementImport(
            UUID.randomUUID(),
            requiredFile.rawFileSha256(),
            requiredFile.rawFileSizeBytes(),
            originalFilename,
            actorIdentityUserId,
            createdAt
        );
    }

    void complete(
        int rowCount,
        int matchedCount,
        int discrepancyCount,
        Instant completedAt
    ) {
        if (status != SettlementImportStatus.PROCESSING) {
            throw new InvalidSettlementImportException(
                "Only a processing import can complete."
            );
        }

        if (
            rowCount < 1
                || rowCount
                    > SettlementCsvParser.MAX_DATA_ROWS
        ) {
            throw new InvalidSettlementImportException(
                "rowCount must be between 1 and 1,000"
            );
        }

        if (
            matchedCount < 0
                || discrepancyCount < 0
                || matchedCount + discrepancyCount
                    != rowCount
        ) {
            throw new InvalidSettlementImportException(
                "Import counts must be non-negative "
                    + "and sum to rowCount."
            );
        }

        Instant timestamp =
            Objects.requireNonNull(
                completedAt,
                "completedAt must not be null"
            );

        if (timestamp.isBefore(createdAt)) {
            throw new InvalidSettlementImportException(
                "completedAt must not precede createdAt."
            );
        }

        this.rowCount = rowCount;
        this.matchedCount = matchedCount;
        this.discrepancyCount = discrepancyCount;
        this.completedAt = timestamp;
        status = SettlementImportStatus.COMPLETED;
    }

    private static String requireFingerprint(
        String rawFileSha256
    ) {
        String required =
            Objects.requireNonNull(
                rawFileSha256,
                "rawFileSha256 must not be null"
            );

        if (!SHA_256.matcher(required).matches()) {
            throw new InvalidSettlementImportException(
                "rawFileSha256 must be lowercase SHA-256"
            );
        }

        return required;
    }

    private static String requireFilename(
        String originalFilename
    ) {
        String required =
            Objects.requireNonNull(
                originalFilename,
                "originalFilename must not be null"
            );
        String candidate = required.strip();

        if (
            candidate.isEmpty()
                || candidate.length()
                    > MAX_FILENAME_LENGTH
        ) {
            throw new InvalidSettlementImportException(
                "originalFilename must contain between "
                    + "1 and 255 characters"
            );
        }

        for (
            int index = 0;
            index < candidate.length();
            index++
        ) {
            if (
                Character.isISOControl(
                    candidate.charAt(index)
                )
            ) {
                throw new InvalidSettlementImportException(
                    "originalFilename must not contain "
                        + "control characters"
                );
            }
        }

        return candidate;
    }

    UUID id() {
        return id;
    }

    String rawFileSha256() {
        return rawFileSha256;
    }

    int rawFileSizeBytes() {
        return rawFileSizeBytes;
    }

    String originalFilename() {
        return originalFilename;
    }

    UUID actorIdentityUserId() {
        return actorIdentityUserId;
    }

    SettlementImportStatus status() {
        return status;
    }

    Integer rowCount() {
        return rowCount;
    }

    Integer matchedCount() {
        return matchedCount;
    }

    Integer discrepancyCount() {
        return discrepancyCount;
    }

    Instant createdAt() {
        return createdAt;
    }

    Instant completedAt() {
        return completedAt;
    }

    long version() {
        return version;
    }
}
