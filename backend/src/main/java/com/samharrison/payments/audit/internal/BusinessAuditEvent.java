package com.samharrison.payments.audit.internal;

import com.samharrison.payments.audit.BusinessAuditActorKind;
import com.samharrison.payments.audit.BusinessAuditEventRequest;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import org.hibernate.annotations.Immutable;

@Entity
@Immutable
@Table(
    name = "business_audit_event",
    uniqueConstraints = {
        @UniqueConstraint(
            name = "uq_business_audit_event_source",
            columnNames = {
                "source_module",
                "event_type",
                "source_record_type",
                "source_record_identifier",
                "source_event_identifier"
            }
        )
    },
    indexes = {
        @Index(
            name = "idx_business_audit_event_time",
            columnList = "occurred_at DESC,id DESC"
        ),
        @Index(
            name = "idx_business_audit_event_subject",
            columnList =
                "subject_type,subject_identifier,"
                    + "occurred_at DESC,id DESC"
        ),
        @Index(
            name = "idx_business_audit_event_actor",
            columnList =
                "actor_identity_user_id,"
                    + "occurred_at DESC,id DESC"
        ),
        @Index(
            name = "idx_business_audit_event_correlation",
            columnList =
                "correlation_identifier,"
                    + "occurred_at DESC,id DESC"
        )
    }
)
class BusinessAuditEvent {

    @Id
    @Column(
        name = "id",
        nullable = false,
        updatable = false
    )
    private UUID id;

    @Column(
        name = "event_type",
        nullable = false,
        updatable = false,
        length = 64
    )
    private String eventType;

    @Column(
        name = "schema_version",
        nullable = false,
        updatable = false
    )
    private int schemaVersion;

    @Column(
        name = "occurred_at",
        nullable = false,
        updatable = false
    )
    private Instant occurredAt;

    @Column(
        name = "recorded_at",
        nullable = false,
        updatable = false
    )
    private Instant recordedAt;

    @Enumerated(EnumType.STRING)
    @Column(
        name = "actor_kind",
        nullable = false,
        updatable = false,
        length = 32
    )
    private BusinessAuditActorKind actorKind;

    @Column(
        name = "actor_identity_user_id",
        updatable = false
    )
    private UUID actorIdentityUserId;

    @Column(
        name = "subject_type",
        nullable = false,
        updatable = false,
        length = 64
    )
    private String subjectType;

    @Column(
        name = "subject_identifier",
        nullable = false,
        updatable = false,
        length = 128
    )
    private String subjectIdentifier;

    @Column(
        name = "source_module",
        nullable = false,
        updatable = false,
        length = 64
    )
    private String sourceModule;

    @Column(
        name = "source_record_type",
        nullable = false,
        updatable = false,
        length = 64
    )
    private String sourceRecordType;

    @Column(
        name = "source_record_identifier",
        nullable = false,
        updatable = false,
        length = 128
    )
    private String sourceRecordIdentifier;

    @Column(
        name = "source_event_identifier",
        nullable = false,
        updatable = false,
        length = 128
    )
    private String sourceEventIdentifier;

    @Column(
        name = "correlation_identifier",
        nullable = false,
        updatable = false,
        length = 128
    )
    private String correlationIdentifier;

    @Column(
        name = "metadata",
        nullable = false,
        updatable = false,
        columnDefinition = "TEXT"
    )
    private String metadata;

    protected BusinessAuditEvent() {
        // Required by JPA.
    }

    private BusinessAuditEvent(
        UUID id,
        BusinessAuditEventRequest request,
        Instant recordedAt,
        String metadata
    ) {
        this.id =
            Objects.requireNonNull(
                id,
                "id must not be null"
            );
        eventType = request.eventType().code();
        schemaVersion =
            request.eventType().schemaVersion();
        occurredAt = request.occurredAt();
        this.recordedAt =
            Objects.requireNonNull(
                recordedAt,
                "recordedAt must not be null"
            );
        actorKind = request.actor().kind();
        actorIdentityUserId =
            request.actor().identityUserId();
        subjectType =
            request.eventType().subjectType();
        subjectIdentifier =
            request.subjectIdentifier();
        sourceModule =
            request.eventType().sourceModule();
        sourceRecordType =
            request.eventType().sourceRecordType();
        sourceRecordIdentifier =
            request.sourceRecordIdentifier();
        sourceEventIdentifier =
            request.sourceEventIdentifier();
        correlationIdentifier =
            request.correlationIdentifier();
        this.metadata =
            Objects.requireNonNull(
                metadata,
                "metadata must not be null"
            );
    }

    static BusinessAuditEvent create(
        BusinessAuditEventRequest request,
        Instant recordedAt,
        String metadata
    ) {
        return new BusinessAuditEvent(
            UUID.randomUUID(),
            Objects.requireNonNull(
                request,
                "request must not be null"
            ),
            recordedAt,
            metadata
        );
    }

    boolean hasSameImmutableContentAs(
        BusinessAuditEvent other
    ) {
        BusinessAuditEvent requiredOther =
            Objects.requireNonNull(
                other,
                "other must not be null"
            );

        return eventType.equals(
            requiredOther.eventType
        )
            && schemaVersion
                == requiredOther.schemaVersion
            && occurredAt.equals(
                requiredOther.occurredAt
            )
            && actorKind
                == requiredOther.actorKind
            && Objects.equals(
                actorIdentityUserId,
                requiredOther.actorIdentityUserId
            )
            && subjectType.equals(
                requiredOther.subjectType
            )
            && subjectIdentifier.equals(
                requiredOther.subjectIdentifier
            )
            && sourceModule.equals(
                requiredOther.sourceModule
            )
            && sourceRecordType.equals(
                requiredOther.sourceRecordType
            )
            && sourceRecordIdentifier.equals(
                requiredOther.sourceRecordIdentifier
            )
            && sourceEventIdentifier.equals(
                requiredOther.sourceEventIdentifier
            )
            && correlationIdentifier.equals(
                requiredOther.correlationIdentifier
            )
            && metadata.equals(
                requiredOther.metadata
            );
    }

    UUID id() {
        return id;
    }

    String eventType() {
        return eventType;
    }

    int schemaVersion() {
        return schemaVersion;
    }

    Instant occurredAt() {
        return occurredAt;
    }

    Instant recordedAt() {
        return recordedAt;
    }

    BusinessAuditActorKind actorKind() {
        return actorKind;
    }

    UUID actorIdentityUserId() {
        return actorIdentityUserId;
    }

    String subjectType() {
        return subjectType;
    }

    String subjectIdentifier() {
        return subjectIdentifier;
    }

    String sourceModule() {
        return sourceModule;
    }

    String sourceRecordType() {
        return sourceRecordType;
    }

    String sourceRecordIdentifier() {
        return sourceRecordIdentifier;
    }

    String sourceEventIdentifier() {
        return sourceEventIdentifier;
    }

    String correlationIdentifier() {
        return correlationIdentifier;
    }

    String metadata() {
        return metadata;
    }
}
