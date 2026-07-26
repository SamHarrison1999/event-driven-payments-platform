CREATE INDEX idx_business_audit_event_type_time
    ON business_audit_event (
        event_type,
        occurred_at DESC,
        id DESC
    );

CREATE INDEX idx_business_audit_event_source_time
    ON business_audit_event (
        source_module,
        occurred_at DESC,
        id DESC
    );

CREATE INDEX idx_identity_security_event_time
    ON identity_security_event (
        occurred_at DESC,
        id DESC
    );

CREATE INDEX idx_identity_security_event_type_time
    ON identity_security_event (
        event_type,
        occurred_at DESC,
        id DESC
    );

CREATE INDEX idx_outbox_replay_audit_time
    ON outbox_replay_audit (
        replayed_at DESC,
        id DESC
    );

CREATE INDEX idx_outbox_replay_audit_actor_time
    ON outbox_replay_audit (
        actor_identity_user_id,
        replayed_at DESC,
        id DESC
    );

CREATE INDEX idx_settlement_resolution_time
    ON settlement_resolution (
        decided_at DESC,
        id DESC
    );

CREATE INDEX idx_settlement_resolution_actor_time
    ON settlement_resolution (
        actor_identity_user_id,
        decided_at DESC,
        id DESC
    );
