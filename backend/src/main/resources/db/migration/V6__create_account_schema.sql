CREATE TABLE customer_account (
    id UUID NOT NULL,
    customer_id UUID NOT NULL,
    currency VARCHAR(3) NOT NULL,
    balance_minor_units BIGINT NOT NULL DEFAULT 0,
    status VARCHAR(32) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,

    CONSTRAINT pk_customer_account
        PRIMARY KEY (id),

    CONSTRAINT fk_customer_account_customer
        FOREIGN KEY (customer_id)
        REFERENCES customer_profile (id)
        ON DELETE RESTRICT,

    CONSTRAINT ck_customer_account_currency
        CHECK (currency = 'GBP'),

    CONSTRAINT ck_customer_account_balance
        CHECK (balance_minor_units >= 0),

    CONSTRAINT ck_customer_account_status
        CHECK (
            status IN (
                'ACTIVE',
                'FROZEN',
                'CLOSED'
            )
        ),

    CONSTRAINT ck_customer_account_closed_balance
        CHECK (
            status <> 'CLOSED'
            OR balance_minor_units = 0
        ),

    CONSTRAINT ck_customer_account_timestamps
        CHECK (created_at <= updated_at),

    CONSTRAINT ck_customer_account_version
        CHECK (version >= 0)
);

CREATE INDEX idx_customer_account_customer
    ON customer_account (customer_id);

CREATE INDEX idx_customer_account_status
    ON customer_account (status);