CREATE TABLE customer_profile (
    id UUID NOT NULL,
    full_name VARCHAR(200) NOT NULL,
    status VARCHAR(32) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,

    CONSTRAINT pk_customer_profile
        PRIMARY KEY (id),

    CONSTRAINT ck_customer_profile_full_name
        CHECK (
            full_name = BTRIM(full_name)
            AND full_name <> ''
            AND full_name !~ '[[:cntrl:]]'
        ),

    CONSTRAINT ck_customer_profile_status
        CHECK (
            status IN (
                'ACTIVE',
                'SUSPENDED',
                'CLOSED'
            )
        ),

    CONSTRAINT ck_customer_profile_timestamps
        CHECK (created_at <= updated_at)
);

CREATE INDEX idx_customer_profile_status
    ON customer_profile (status);