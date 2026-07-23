package com.samharrison.payments.outbox.internal;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(name = "outbox_replay_audit")
class OutboxReplayAudit {

    @Id
    @Column(
        name = "id",
        nullable = false,
        updatable = false
    )
    private UUID id;

    @Column(
        name = "event_id",
        nullable = false,
        updatable = false
    )
    private UUID eventId;

    @Column(
        name = "actor_identity_user_id",
        nullable = false,
        updatable = false
    )
    private UUID actorIdentityUserId;

    @Column(
        name = "reason",
        nullable = false,
        updatable = false,
        length = 500
    )
    private String reason;

    @Column(
        name = "replayed_at",
        nullable = false,
        updatable = false
    )
    private Instant replayedAt;

    @Column(
        name = "event_version_before",
        nullable = false,
        updatable = false
    )
    private long eventVersionBefore;

    protected OutboxReplayAudit() {
        // Required by JPA.
    }

    private OutboxReplayAudit(
        UUID eventId,
        UUID actorIdentityUserId,
        String reason,
        Instant replayedAt,
        long eventVersionBefore
    ) {
        id = UUID.randomUUID();

        this.eventId =
            Objects.requireNonNull(
                eventId,
                "eventId must not be null"
            );

        this.actorIdentityUserId =
            Objects.requireNonNull(
                actorIdentityUserId,
                "actorIdentityUserId must not be null"
            );

        this.reason =
            Objects.requireNonNull(
                reason,
                "reason must not be null"
            );

        this.replayedAt =
            Objects.requireNonNull(
                replayedAt,
                "replayedAt must not be null"
            );

        if (eventVersionBefore < 0) {
            throw new IllegalArgumentException(
                "eventVersionBefore must not be negative"
            );
        }

        this.eventVersionBefore =
            eventVersionBefore;
    }

    static OutboxReplayAudit recorded(
        UUID eventId,
        UUID actorIdentityUserId,
        String reason,
        Instant replayedAt,
        long eventVersionBefore
    ) {
        return new OutboxReplayAudit(
            eventId,
            actorIdentityUserId,
            reason,
            replayedAt,
            eventVersionBefore
        );
    }

    UUID id() {
        return id;
    }
}
