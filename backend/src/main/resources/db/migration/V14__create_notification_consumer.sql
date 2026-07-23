CREATE TABLE notification_consumer_checkpoint (
    consumer_name VARCHAR(64) NOT NULL,
    last_published_at TIMESTAMP WITH TIME ZONE NOT NULL,
    last_event_id UUID NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    version BIGINT NOT NULL,

    CONSTRAINT pk_notification_consumer_checkpoint
        PRIMARY KEY (consumer_name),

    CONSTRAINT ck_notification_consumer_name
        CHECK (
            consumer_name
                ~ '^[a-z][a-z0-9._-]{0,63}$'
        ),

    CONSTRAINT ck_notification_checkpoint_version
        CHECK (version >= 0)
);

INSERT INTO notification_consumer_checkpoint (
    consumer_name,
    last_published_at,
    last_event_id,
    updated_at,
    version
)
VALUES (
    'notification.payment-completed.v1',
    TIMESTAMP WITH TIME ZONE
        '1970-01-01 00:00:00+00',
    '00000000-0000-0000-0000-000000000000',
    TIMESTAMP WITH TIME ZONE
        '1970-01-01 00:00:00+00',
    0
);

CREATE TABLE notification (
    id UUID NOT NULL,
    source_event_id UUID NOT NULL,
    recipient_identity_user_id UUID NOT NULL,
    payment_id UUID NOT NULL,
    amount_minor_units BIGINT NOT NULL,
    currency VARCHAR(3) NOT NULL,
    payment_completed_at
        TIMESTAMP WITH TIME ZONE NOT NULL,
    status VARCHAR(32) NOT NULL,
    attempt_count INTEGER NOT NULL,
    next_attempt_at TIMESTAMP WITH TIME ZONE,
    delivery_owner_token UUID,
    delivery_lease_expires_at
        TIMESTAMP WITH TIME ZONE,
    last_error_category VARCHAR(64),
    last_error_message VARCHAR(512),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    delivered_at TIMESTAMP WITH TIME ZONE,
    version BIGINT NOT NULL,

    CONSTRAINT pk_notification
        PRIMARY KEY (id),

    CONSTRAINT uq_notification_source_event
        UNIQUE (source_event_id),

    CONSTRAINT ck_notification_amount
        CHECK (amount_minor_units > 0),

    CONSTRAINT ck_notification_currency
        CHECK (currency = 'GBP'),

    CONSTRAINT ck_notification_status
        CHECK (
            status IN (
                'PENDING',
                'DELIVERING',
                'DELIVERED',
                'DEAD_LETTER'
            )
        ),

    CONSTRAINT ck_notification_attempt_count
        CHECK (
            attempt_count BETWEEN 0 AND 100
        ),

    CONSTRAINT ck_notification_timestamps
        CHECK (
            updated_at >= created_at
            AND payment_completed_at <= created_at
        ),

    CONSTRAINT ck_notification_state
        CHECK (
            (
                status = 'PENDING'
                AND next_attempt_at IS NOT NULL
                AND delivery_owner_token IS NULL
                AND delivery_lease_expires_at IS NULL
                AND delivered_at IS NULL
            )
            OR (
                status = 'DELIVERING'
                AND next_attempt_at IS NULL
                AND delivery_owner_token IS NOT NULL
                AND delivery_lease_expires_at
                    IS NOT NULL
                AND delivery_lease_expires_at
                    > updated_at
                AND delivered_at IS NULL
            )
            OR (
                status = 'DELIVERED'
                AND next_attempt_at IS NULL
                AND delivery_owner_token IS NULL
                AND delivery_lease_expires_at IS NULL
                AND delivered_at IS NOT NULL
                AND delivered_at >= created_at
            )
            OR (
                status = 'DEAD_LETTER'
                AND next_attempt_at IS NULL
                AND delivery_owner_token IS NULL
                AND delivery_lease_expires_at IS NULL
                AND last_error_category IS NOT NULL
                AND last_error_message IS NOT NULL
                AND delivered_at IS NULL
            )
        ),

    CONSTRAINT ck_notification_version
        CHECK (version >= 0)
);

CREATE INDEX idx_notification_recipient
    ON notification (
        recipient_identity_user_id,
        created_at DESC,
        id DESC
    );

CREATE INDEX idx_notification_claim
    ON notification (
        status,
        next_attempt_at,
        created_at,
        id
    )
    WHERE status = 'PENDING';

CREATE INDEX idx_notification_lease
    ON notification (
        status,
        delivery_lease_expires_at,
        id
    )
    WHERE status = 'DELIVERING';

CREATE TABLE notification_consumer_failure (
    id UUID NOT NULL,
    source_event_id UUID NOT NULL,
    event_type VARCHAR(128) NOT NULL,
    schema_version INTEGER NOT NULL,
    error_category VARCHAR(64) NOT NULL,
    error_message VARCHAR(512) NOT NULL,
    occurred_at TIMESTAMP WITH TIME ZONE NOT NULL,

    CONSTRAINT pk_notification_consumer_failure
        PRIMARY KEY (id),

    CONSTRAINT uq_notification_consumer_failure_event
        UNIQUE (source_event_id),

    CONSTRAINT ck_notification_failure_event_type
        CHECK (
            event_type
                ~ '^[a-z][a-z0-9._-]{0,127}$'
        ),

    CONSTRAINT ck_notification_failure_schema
        CHECK (schema_version > 0)
);

CREATE INDEX idx_notification_consumer_failure_time
    ON notification_consumer_failure (
        occurred_at DESC,
        id DESC
    );
