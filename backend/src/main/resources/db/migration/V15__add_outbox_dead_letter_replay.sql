ALTER TABLE outbox_event
    ADD COLUMN replay_count INTEGER NOT NULL
        DEFAULT 0,
    ADD COLUMN last_replayed_at
        TIMESTAMP WITH TIME ZONE;

ALTER TABLE outbox_event
    ADD CONSTRAINT ck_outbox_event_replay_count
        CHECK (replay_count BETWEEN 0 AND 1000),
    ADD CONSTRAINT ck_outbox_event_replay_time
        CHECK (
            (
                replay_count = 0
                AND last_replayed_at IS NULL
            )
            OR (
                replay_count > 0
                AND last_replayed_at IS NOT NULL
                AND last_replayed_at >= created_at
            )
        );

CREATE TABLE outbox_replay_audit (
    id UUID NOT NULL,
    event_id UUID NOT NULL,
    actor_identity_user_id UUID NOT NULL,
    reason VARCHAR(500) NOT NULL,
    replayed_at TIMESTAMP WITH TIME ZONE NOT NULL,
    event_version_before BIGINT NOT NULL,

    CONSTRAINT pk_outbox_replay_audit
        PRIMARY KEY (id),

    CONSTRAINT fk_outbox_replay_audit_event
        FOREIGN KEY (event_id)
        REFERENCES outbox_event (id),

    CONSTRAINT ck_outbox_replay_audit_reason
        CHECK (
            CHAR_LENGTH(BTRIM(reason))
                BETWEEN 1 AND 500
        ),

    CONSTRAINT ck_outbox_replay_audit_version
        CHECK (event_version_before >= 0)
);

CREATE INDEX idx_outbox_replay_audit_event
    ON outbox_replay_audit (
        event_id,
        replayed_at DESC,
        id DESC
    );

CREATE OR REPLACE FUNCTION
    reject_outbox_replay_audit_mutation()
RETURNS TRIGGER
LANGUAGE plpgsql
AS $$
BEGIN
    RAISE EXCEPTION
        'outbox_replay_audit is immutable';
END;
$$;

CREATE TRIGGER trg_outbox_replay_audit_immutable
BEFORE UPDATE OR DELETE
ON outbox_replay_audit
FOR EACH ROW
EXECUTE FUNCTION
    reject_outbox_replay_audit_mutation();
