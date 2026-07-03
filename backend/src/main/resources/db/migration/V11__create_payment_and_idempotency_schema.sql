CREATE TABLE payment (
    id UUID NOT NULL,
    actor_identity_id UUID NOT NULL,
    source_account_id UUID NOT NULL,
    destination_account_id UUID NOT NULL,
    amount_minor_units BIGINT NOT NULL,
    currency VARCHAR(3) NOT NULL,
    status VARCHAR(32) NOT NULL,
    ledger_transaction_id UUID,
    rejection_reason VARCHAR(64),
    failure_reason VARCHAR(64),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    version BIGINT NOT NULL,

    CONSTRAINT pk_payment
        PRIMARY KEY (id),

    CONSTRAINT fk_payment_actor
        FOREIGN KEY (actor_identity_id)
        REFERENCES identity_user (id)
        ON DELETE RESTRICT,

    CONSTRAINT fk_payment_source_account
        FOREIGN KEY (source_account_id)
        REFERENCES customer_account (id)
        ON DELETE RESTRICT,

    CONSTRAINT fk_payment_destination_account
        FOREIGN KEY (destination_account_id)
        REFERENCES customer_account (id)
        ON DELETE RESTRICT,

    CONSTRAINT fk_payment_ledger_transaction
        FOREIGN KEY (ledger_transaction_id)
        REFERENCES ledger_transaction (id)
        ON DELETE RESTRICT,

    CONSTRAINT uq_payment_ledger_transaction
        UNIQUE (ledger_transaction_id),

    CONSTRAINT ck_payment_accounts_different
        CHECK (
            source_account_id
                <> destination_account_id
        ),

    CONSTRAINT ck_payment_amount
        CHECK (amount_minor_units > 0),

    CONSTRAINT ck_payment_currency
        CHECK (currency = 'GBP'),

    CONSTRAINT ck_payment_status
        CHECK (
            status IN (
                'PENDING',
                'PROCESSING',
                'COMPLETED',
                'REJECTED',
                'FAILED'
            )
        ),

    CONSTRAINT ck_payment_rejection_reason
        CHECK (
            rejection_reason IS NULL
            OR rejection_reason IN (
                'SOURCE_NOT_OWNED',
                'SOURCE_NOT_FOUND',
                'DESTINATION_NOT_FOUND',
                'SOURCE_NOT_ACTIVE',
                'DESTINATION_NOT_ACTIVE',
                'CURRENCY_MISMATCH',
                'INSUFFICIENT_FUNDS'
            )
        ),

    CONSTRAINT ck_payment_failure_reason
        CHECK (
            failure_reason IS NULL
            OR failure_reason IN (
                'PROCESSING_FAILED',
                'CONCURRENT_MODIFICATION'
            )
        ),

    CONSTRAINT ck_payment_terminal_details
        CHECK (
            (
                status IN (
                    'PENDING',
                    'PROCESSING'
                )
                AND ledger_transaction_id IS NULL
                AND rejection_reason IS NULL
                AND failure_reason IS NULL
            )
            OR (
                status = 'COMPLETED'
                AND ledger_transaction_id IS NOT NULL
                AND rejection_reason IS NULL
                AND failure_reason IS NULL
            )
            OR (
                status = 'REJECTED'
                AND ledger_transaction_id IS NULL
                AND rejection_reason IS NOT NULL
                AND failure_reason IS NULL
            )
            OR (
                status = 'FAILED'
                AND ledger_transaction_id IS NULL
                AND rejection_reason IS NULL
                AND failure_reason IS NOT NULL
            )
        ),

    CONSTRAINT ck_payment_timestamps
        CHECK (updated_at >= created_at),

    CONSTRAINT ck_payment_version
        CHECK (version >= 0)
);

CREATE INDEX idx_payment_actor_created
    ON payment (
        actor_identity_id,
        created_at,
        id
    );

CREATE INDEX idx_payment_source_account
    ON payment (
        source_account_id,
        created_at,
        id
    );

CREATE INDEX idx_payment_destination_account
    ON payment (
        destination_account_id,
        created_at,
        id
    );

CREATE INDEX idx_payment_status_updated
    ON payment (
        status,
        updated_at,
        id
    );

CREATE TABLE payment_idempotency (
    id UUID NOT NULL,
    actor_identity_id UUID NOT NULL,
    operation VARCHAR(64) NOT NULL,
    idempotency_key VARCHAR(128) NOT NULL,
    request_fingerprint VARCHAR(64) NOT NULL,
    payment_id UUID NOT NULL,
    status VARCHAR(32) NOT NULL,
    processing_owner_token UUID,
    processing_lease_expires_at
        TIMESTAMP WITH TIME ZONE,
    response_status INTEGER,
    response_media_type VARCHAR(64),
    response_body TEXT,
    retention_expires_at
        TIMESTAMP WITH TIME ZONE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    version BIGINT NOT NULL,

    CONSTRAINT pk_payment_idempotency
        PRIMARY KEY (id),

    CONSTRAINT fk_payment_idempotency_actor
        FOREIGN KEY (actor_identity_id)
        REFERENCES identity_user (id)
        ON DELETE RESTRICT,

    CONSTRAINT fk_payment_idempotency_payment
        FOREIGN KEY (payment_id)
        REFERENCES payment (id)
        ON DELETE RESTRICT,

    CONSTRAINT uq_payment_idempotency_scope
        UNIQUE (
            actor_identity_id,
            operation,
            idempotency_key
        ),

    CONSTRAINT uq_payment_idempotency_payment
        UNIQUE (payment_id),

    CONSTRAINT ck_payment_idempotency_operation
        CHECK (
            operation = 'CREATE_INTERNAL_PAYMENT'
        ),

    CONSTRAINT ck_payment_idempotency_key
        CHECK (
            CHAR_LENGTH(idempotency_key)
                BETWEEN 1 AND 128
            AND idempotency_key ~ '^[!-~]+$'
        ),

    CONSTRAINT ck_payment_idempotency_fingerprint
        CHECK (
            request_fingerprint
                ~ '^[0-9a-f]{64}$'
        ),

    CONSTRAINT ck_payment_idempotency_status
        CHECK (
            status IN (
                'PROCESSING',
                'COMPLETED'
            )
        ),

    CONSTRAINT ck_payment_idempotency_response_status
        CHECK (
            response_status IS NULL
            OR response_status BETWEEN 100 AND 599
        ),

    CONSTRAINT ck_payment_idempotency_media_type
        CHECK (
            response_media_type IS NULL
            OR response_media_type IN (
                'application/json',
                'application/problem+json'
            )
        ),

    CONSTRAINT ck_payment_idempotency_response_size
        CHECK (
            response_body IS NULL
            OR (
                response_body <> ''
                AND OCTET_LENGTH(
                    response_body
                ) <= 16384
            )
        ),

    CONSTRAINT ck_payment_idempotency_state
        CHECK (
            (
                status = 'PROCESSING'
                AND processing_owner_token IS NOT NULL
                AND processing_lease_expires_at
                    IS NOT NULL
                AND processing_lease_expires_at
                    > updated_at
                AND response_status IS NULL
                AND response_media_type IS NULL
                AND response_body IS NULL
                AND retention_expires_at IS NULL
            )
            OR (
                status = 'COMPLETED'
                AND processing_owner_token IS NULL
                AND processing_lease_expires_at IS NULL
                AND response_status IS NOT NULL
                AND response_media_type IS NOT NULL
                AND response_body IS NOT NULL
                AND retention_expires_at IS NOT NULL
                AND retention_expires_at
                    > updated_at
            )
        ),

    CONSTRAINT ck_payment_idempotency_timestamps
        CHECK (updated_at >= created_at),

    CONSTRAINT ck_payment_idempotency_version
        CHECK (version >= 0)
);

CREATE INDEX idx_payment_idempotency_lease
    ON payment_idempotency (
        status,
        processing_lease_expires_at,
        id
    )
    WHERE status = 'PROCESSING';

CREATE INDEX idx_payment_idempotency_retention
    ON payment_idempotency (
        status,
        retention_expires_at,
        id
    )
    WHERE status = 'COMPLETED';