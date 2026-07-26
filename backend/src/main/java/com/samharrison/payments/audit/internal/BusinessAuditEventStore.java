package com.samharrison.payments.audit.internal;

import com.samharrison.payments.audit.BusinessAuditEventConflictException;
import com.samharrison.payments.audit.RecordedBusinessAuditEvent;
import java.sql.Timestamp;
import java.util.Objects;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
class BusinessAuditEventStore {

    private static final String INSERT_SQL = """
        INSERT INTO business_audit_event (
            id,
            event_type,
            schema_version,
            occurred_at,
            recorded_at,
            actor_kind,
            actor_identity_user_id,
            subject_type,
            subject_identifier,
            source_module,
            source_record_type,
            source_record_identifier,
            source_event_identifier,
            correlation_identifier,
            metadata
        )
        VALUES (
            ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?
        )
        ON CONFLICT (
            source_module,
            event_type,
            source_record_type,
            source_record_identifier,
            source_event_identifier
        )
        DO NOTHING
        """;

    private final JdbcTemplate jdbcTemplate;
    private final BusinessAuditEventRepository repository;

    BusinessAuditEventStore(
        JdbcTemplate jdbcTemplate,
        BusinessAuditEventRepository repository
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

    RecordedBusinessAuditEvent record(
        BusinessAuditEvent candidate
    ) {
        BusinessAuditEvent requiredCandidate =
            Objects.requireNonNull(
                candidate,
                "candidate must not be null"
            );

        int inserted =
            jdbcTemplate.update(
                INSERT_SQL,
                requiredCandidate.id(),
                requiredCandidate.eventType(),
                requiredCandidate.schemaVersion(),
                Timestamp.from(
                    requiredCandidate.occurredAt()
                ),
                Timestamp.from(
                    requiredCandidate.recordedAt()
                ),
                requiredCandidate.actorKind().name(),
                requiredCandidate.actorIdentityUserId(),
                requiredCandidate.subjectType(),
                requiredCandidate.subjectIdentifier(),
                requiredCandidate.sourceModule(),
                requiredCandidate.sourceRecordType(),
                requiredCandidate
                    .sourceRecordIdentifier(),
                requiredCandidate
                    .sourceEventIdentifier(),
                requiredCandidate
                    .correlationIdentifier(),
                requiredCandidate.metadata()
            );

        if (inserted == 1) {
            return new RecordedBusinessAuditEvent(
                requiredCandidate.id(),
                false
            );
        }

        BusinessAuditEvent existing =
            repository
                .findExisting(
                    requiredCandidate.sourceModule(),
                    requiredCandidate.eventType(),
                    requiredCandidate.sourceRecordType(),
                    requiredCandidate
                        .sourceRecordIdentifier(),
                    requiredCandidate
                        .sourceEventIdentifier()
                )
                .orElseThrow(
                    () ->
                        new IllegalStateException(
                            "An audit source-event "
                                + "reservation could not "
                                + "be read."
                        )
                );

        if (
            !existing.hasSameImmutableContentAs(
                requiredCandidate
            )
        ) {
            throw new
                BusinessAuditEventConflictException(
                    requiredCandidate
                        .sourceEventIdentifier()
                );
        }

        return new RecordedBusinessAuditEvent(
            existing.id(),
            true
        );
    }
}
