CREATE TABLE outbox_event (
    id UUID NOT NULL,
    aggregate_type VARCHAR(64) NOT NULL,
    aggregate_id UUID NOT NULL,
    event_type VARCHAR(128) NOT NULL,
    schema_version INTEGER NOT NULL,
    payload TEXT NOT NULL,
    correlation_id VARCHAR(128) NOT NULL,
    causation_id VARCHAR(128),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    status VARCHAR(32) NOT NULL,
    attempt_count INTEGER NOT NULL,
    next_attempt_at TIMESTAMP WITH TIME ZONE,
    publication_owner_token UUID,
    publication_lease_expires_at
        TIMESTAMP WITH TIME ZONE,
    last_error_category VARCHAR(64),
    last_error_message VARCHAR(512),
    published_at TIMESTAMP WITH TIME ZONE,
    version BIGINT NOT NULL,

    CONSTRAINT pk_outbox_event
        PRIMARY KEY (id),

    CONSTRAINT uq_outbox_event_aggregate_type
        UNIQUE (
            aggregate_type,
            aggregate_id,
            event_type
        ),

    CONSTRAINT ck_outbox_event_aggregate_type
        CHECK (
            aggregate_type
                ~ '^[a-z][a-z0-9._-]{0,63}$'
        ),

    CONSTRAINT ck_outbox_event_event_type
        CHECK (
            event_type
                ~ '^[a-z][a-z0-9._-]{0,127}$'
        ),

    CONSTRAINT ck_outbox_event_schema_version
        CHECK (schema_version > 0),

    CONSTRAINT ck_outbox_event_payload
        CHECK (
            payload IS JSON OBJECT
            AND OCTET_LENGTH(payload) <= 32768
        ),

    CONSTRAINT ck_outbox_event_correlation
        CHECK (
            correlation_id
                ~ '^[A-Za-z0-9._-]{1,128}$'
        ),

    CONSTRAINT ck_outbox_event_causation
        CHECK (
            causation_id IS NULL
            OR causation_id
                ~ '^[A-Za-z0-9._-]{1,128}$'
        ),

    CONSTRAINT ck_outbox_event_status
        CHECK (
            status IN (
                'PENDING',
                'PUBLISHING',
                'PUBLISHED',
                'DEAD_LETTER'
            )
        ),

    CONSTRAINT ck_outbox_event_attempt_count
        CHECK (
            attempt_count BETWEEN 0 AND 100
        ),

    CONSTRAINT ck_outbox_event_timestamps
        CHECK (
            updated_at >= created_at
        ),

    CONSTRAINT ck_outbox_event_state
        CHECK (
            (
                status = 'PENDING'
                AND next_attempt_at IS NOT NULL
                AND publication_owner_token IS NULL
                AND publication_lease_expires_at IS NULL
                AND published_at IS NULL
            )
            OR (
                status = 'PUBLISHING'
                AND next_attempt_at IS NULL
                AND publication_owner_token IS NOT NULL
                AND publication_lease_expires_at
                    IS NOT NULL
                AND publication_lease_expires_at
                    > updated_at
                AND published_at IS NULL
            )
            OR (
                status = 'PUBLISHED'
                AND next_attempt_at IS NULL
                AND publication_owner_token IS NULL
                AND publication_lease_expires_at IS NULL
                AND published_at IS NOT NULL
                AND published_at >= created_at
            )
            OR (
                status = 'DEAD_LETTER'
                AND next_attempt_at IS NULL
                AND publication_owner_token IS NULL
                AND publication_lease_expires_at IS NULL
                AND last_error_category IS NOT NULL
                AND last_error_message IS NOT NULL
                AND published_at IS NULL
            )
        ),

    CONSTRAINT ck_outbox_event_version
        CHECK (version >= 0)
);

CREATE INDEX idx_outbox_event_claim
    ON outbox_event (
        status,
        next_attempt_at,
        created_at,
        id
    )
    WHERE status = 'PENDING';

CREATE INDEX idx_outbox_event_lease
    ON outbox_event (
        status,
        publication_lease_expires_at,
        id
    )
    WHERE status = 'PUBLISHING';

CREATE INDEX idx_outbox_event_aggregate
    ON outbox_event (
        aggregate_type,
        aggregate_id,
        created_at,
        id
    );
