package com.samharrison.payments.identity.internal;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(
    name = "identity_security_event",
    indexes = {
        @Index(
            name =
                "idx_identity_security_event_subject",
            columnList =
                "subject_user_id, occurred_at"
        ),
        @Index(
            name =
                "idx_identity_security_event_actor",
            columnList =
                "actor_user_id, occurred_at"
        )
    }
)
public class IdentitySecurityEvent {

    @Id
    @Column(
        name = "id",
        nullable = false,
        updatable = false
    )
    private UUID id;

    @Enumerated(EnumType.STRING)
    @Column(
        name = "event_type",
        nullable = false,
        updatable = false,
        length = 64
    )
    private IdentitySecurityEventType eventType;

    @Column(
        name = "actor_user_id",
        nullable = false,
        updatable = false
    )
    private UUID actorUserId;

    @Column(
        name = "subject_user_id",
        nullable = false,
        updatable = false
    )
    private UUID subjectUserId;

    @Enumerated(EnumType.STRING)
    @Column(
        name = "role_code",
        nullable = false,
        updatable = false,
        length = 32
    )
    private IdentityRole role;

    @Column(
        name = "occurred_at",
        nullable = false,
        updatable = false
    )
    private Instant occurredAt;

    protected IdentitySecurityEvent() {
        // Required by JPA.
    }

    private IdentitySecurityEvent(
        IdentitySecurityEventType eventType,
        UUID actorUserId,
        UUID subjectUserId,
        IdentityRole role,
        Instant occurredAt
    ) {
        this.id = UUID.randomUUID();

        this.eventType =
            Objects.requireNonNull(
                eventType,
                "eventType must not be null"
            );

        this.actorUserId =
            Objects.requireNonNull(
                actorUserId,
                "actorUserId must not be null"
            );

        this.subjectUserId =
            Objects.requireNonNull(
                subjectUserId,
                "subjectUserId must not be null"
            );

        this.role =
            Objects.requireNonNull(
                role,
                "role must not be null"
            );

        this.occurredAt =
            Objects.requireNonNull(
                occurredAt,
                "occurredAt must not be null"
            );
    }

    static IdentitySecurityEvent roleGranted(
        UUID actorUserId,
        UUID subjectUserId,
        IdentityRole role,
        Instant occurredAt
    ) {
        return new IdentitySecurityEvent(
            IdentitySecurityEventType.ROLE_GRANTED,
            actorUserId,
            subjectUserId,
            role,
            occurredAt
        );
    }

    static IdentitySecurityEvent roleRevoked(
        UUID actorUserId,
        UUID subjectUserId,
        IdentityRole role,
        Instant occurredAt
    ) {
        return new IdentitySecurityEvent(
            IdentitySecurityEventType.ROLE_REVOKED,
            actorUserId,
            subjectUserId,
            role,
            occurredAt
        );
    }

    UUID id() {
        return id;
    }

    IdentitySecurityEventType eventType() {
        return eventType;
    }

    UUID actorUserId() {
        return actorUserId;
    }

    UUID subjectUserId() {
        return subjectUserId;
    }

    IdentityRole role() {
        return role;
    }

    Instant occurredAt() {
        return occurredAt;
    }
}
