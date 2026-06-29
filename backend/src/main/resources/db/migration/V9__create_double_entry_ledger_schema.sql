CREATE TABLE ledger_transaction (
    id UUID NOT NULL,
    transaction_type VARCHAR(64) NOT NULL,
    business_reference VARCHAR(100),
    corrects_transaction_id UUID,
    posted_at TIMESTAMP WITH TIME ZONE NOT NULL,
    description VARCHAR(500) NOT NULL,

    CONSTRAINT pk_ledger_transaction
        PRIMARY KEY (id),

    CONSTRAINT fk_ledger_transaction_correction
        FOREIGN KEY (corrects_transaction_id)
        REFERENCES ledger_transaction (id)
        ON DELETE RESTRICT,

    CONSTRAINT ck_ledger_transaction_type
        CHECK (
            transaction_type ~ '^[A-Z][A-Z0-9_]*$'
            AND CHAR_LENGTH(transaction_type) <= 64
        ),

    CONSTRAINT ck_ledger_transaction_reference
        CHECK (
            business_reference IS NULL
            OR (
                business_reference = BTRIM(business_reference)
                AND business_reference <> ''
                AND CHAR_LENGTH(business_reference) <= 100
                AND business_reference !~ '[[:cntrl:]]'
            )
        ),

    CONSTRAINT ck_ledger_transaction_description
        CHECK (
            description = BTRIM(description)
            AND description <> ''
            AND CHAR_LENGTH(description) <= 500
            AND description !~ '[[:cntrl:]]'
        ),

    CONSTRAINT ck_ledger_transaction_not_self_correction
        CHECK (
            corrects_transaction_id IS NULL
            OR corrects_transaction_id <> id
        )
);

CREATE INDEX idx_ledger_transaction_posted
    ON ledger_transaction (posted_at, id);

CREATE INDEX idx_ledger_transaction_reference
    ON ledger_transaction (business_reference)
    WHERE business_reference IS NOT NULL;

CREATE INDEX idx_ledger_transaction_correction
    ON ledger_transaction (corrects_transaction_id)
    WHERE corrects_transaction_id IS NOT NULL;

CREATE TABLE ledger_entry (
    id UUID NOT NULL,
    transaction_id UUID NOT NULL,
    ledger_account_id UUID NOT NULL,
    side VARCHAR(16) NOT NULL,
    amount_minor_units BIGINT NOT NULL,
    currency VARCHAR(3) NOT NULL,
    entry_sequence INTEGER NOT NULL,
    description VARCHAR(200) NOT NULL,

    CONSTRAINT pk_ledger_entry
        PRIMARY KEY (id),

    CONSTRAINT fk_ledger_entry_transaction
        FOREIGN KEY (transaction_id)
        REFERENCES ledger_transaction (id)
        ON DELETE RESTRICT,

    CONSTRAINT fk_ledger_entry_account
        FOREIGN KEY (ledger_account_id)
        REFERENCES customer_account (id)
        ON DELETE RESTRICT,

    CONSTRAINT uq_ledger_entry_transaction_sequence
        UNIQUE (transaction_id, entry_sequence),

    CONSTRAINT ck_ledger_entry_side
        CHECK (side IN ('DEBIT', 'CREDIT')),

    CONSTRAINT ck_ledger_entry_amount
        CHECK (amount_minor_units > 0),

    CONSTRAINT ck_ledger_entry_currency
        CHECK (currency = 'GBP'),

    CONSTRAINT ck_ledger_entry_sequence
        CHECK (entry_sequence > 0),

    CONSTRAINT ck_ledger_entry_description
        CHECK (
            description = BTRIM(description)
            AND description <> ''
            AND CHAR_LENGTH(description) <= 200
            AND description !~ '[[:cntrl:]]'
        )
);

CREATE INDEX idx_ledger_entry_transaction
    ON ledger_entry (transaction_id, entry_sequence);

CREATE INDEX idx_ledger_entry_account
    ON ledger_entry (ledger_account_id, transaction_id);