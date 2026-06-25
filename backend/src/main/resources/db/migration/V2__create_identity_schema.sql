CREATE TABLE identity_user (
    id UUID NOT NULL,
    email VARCHAR(320) NOT NULL,
    normalized_email VARCHAR(320) NOT NULL,
    password_hash VARCHAR(255) NOT NULL,
    status VARCHAR(32) NOT NULL,
    failed_login_attempts INTEGER NOT NULL DEFAULT 0,
    locked_until TIMESTAMP WITH TIME ZONE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,

    CONSTRAINT pk_identity_user
        PRIMARY KEY (id),

    CONSTRAINT uq_identity_user_normalized_email
        UNIQUE (normalized_email),

    CONSTRAINT ck_identity_user_email_trimmed
        CHECK (
            email = BTRIM(email)
            AND email <> ''
        ),

    CONSTRAINT ck_identity_user_normalized_email
        CHECK (
            normalized_email = LOWER(BTRIM(normalized_email))
            AND normalized_email <> ''
        ),

    CONSTRAINT ck_identity_user_password_hash
        CHECK (BTRIM(password_hash) <> ''),

    CONSTRAINT ck_identity_user_status
        CHECK (
            status IN (
                'ACTIVE',
                'LOCKED',
                'DISABLED'
            )
        ),

    CONSTRAINT ck_identity_user_failed_login_attempts
        CHECK (failed_login_attempts >= 0),

    CONSTRAINT ck_identity_user_lock_state
        CHECK (
            (
                status = 'LOCKED'
                AND locked_until IS NOT NULL
            )
            OR
            (
                status <> 'LOCKED'
                AND locked_until IS NULL
            )
        ),

    CONSTRAINT ck_identity_user_timestamps
        CHECK (created_at <= updated_at)
);

CREATE TABLE identity_user_role (
    user_id UUID NOT NULL,
    role_code VARCHAR(32) NOT NULL,

    CONSTRAINT pk_identity_user_role
        PRIMARY KEY (user_id, role_code),

    CONSTRAINT fk_identity_user_role_user
        FOREIGN KEY (user_id)
        REFERENCES identity_user (id)
        ON DELETE CASCADE,

    CONSTRAINT ck_identity_user_role_code
        CHECK (
            role_code IN (
                'CUSTOMER',
                'OPERATIONS',
                'RECONCILIATION_ANALYST',
                'ADMIN'
            )
        )
);

CREATE INDEX idx_identity_user_status
    ON identity_user (status);
