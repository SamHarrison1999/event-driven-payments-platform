CREATE TABLE settlement_import (
    id UUID NOT NULL,
    raw_file_sha256 VARCHAR(64) NOT NULL,
    raw_file_size_bytes INTEGER NOT NULL,
    original_filename VARCHAR(255) NOT NULL,
    actor_identity_user_id UUID NOT NULL,
    status VARCHAR(32) NOT NULL,
    row_count INTEGER,
    matched_count INTEGER,
    discrepancy_count INTEGER,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    completed_at TIMESTAMP WITH TIME ZONE,
    version BIGINT NOT NULL,

    CONSTRAINT pk_settlement_import
        PRIMARY KEY (id),

    CONSTRAINT fk_settlement_import_actor
        FOREIGN KEY (actor_identity_user_id)
        REFERENCES identity_user (id)
        ON DELETE RESTRICT,

    CONSTRAINT uq_settlement_import_fingerprint
        UNIQUE (raw_file_sha256),

    CONSTRAINT ck_settlement_import_fingerprint
        CHECK (
            raw_file_sha256 ~ '^[0-9a-f]{64}$'
        ),

    CONSTRAINT ck_settlement_import_file_size
        CHECK (
            raw_file_size_bytes
                BETWEEN 1 AND 1048576
        ),

    CONSTRAINT ck_settlement_import_filename
        CHECK (
            CHAR_LENGTH(BTRIM(original_filename))
                BETWEEN 1 AND 255
            AND original_filename
                !~ '[[:cntrl:]]'
        ),

    CONSTRAINT ck_settlement_import_status
        CHECK (
            status IN (
                'PROCESSING',
                'COMPLETED'
            )
        ),

    CONSTRAINT ck_settlement_import_state
        CHECK (
            (
                status = 'PROCESSING'
                AND row_count IS NULL
                AND matched_count IS NULL
                AND discrepancy_count IS NULL
                AND completed_at IS NULL
            )
            OR (
                status = 'COMPLETED'
                AND row_count IS NOT NULL
                AND matched_count IS NOT NULL
                AND discrepancy_count IS NOT NULL
                AND row_count BETWEEN 1 AND 1000
                AND matched_count >= 0
                AND discrepancy_count >= 0
                AND matched_count
                    + discrepancy_count
                    = row_count
                AND completed_at IS NOT NULL
                AND completed_at >= created_at
            )
        ),

    CONSTRAINT ck_settlement_import_version
        CHECK (version >= 0)
);

CREATE INDEX idx_settlement_import_created
    ON settlement_import (
        created_at DESC,
        id DESC
    );

CREATE TABLE settlement_record (
    id UUID NOT NULL,
    settlement_import_id UUID NOT NULL,
    row_number INTEGER NOT NULL,
    settlement_record_id VARCHAR(128) NOT NULL,
    payment_id UUID NOT NULL,
    amount_minor_units BIGINT NOT NULL,
    currency VARCHAR(3) NOT NULL,
    settled_at TIMESTAMP WITH TIME ZONE NOT NULL,

    CONSTRAINT pk_settlement_record
        PRIMARY KEY (id),

    CONSTRAINT fk_settlement_record_import
        FOREIGN KEY (settlement_import_id)
        REFERENCES settlement_import (id)
        ON DELETE RESTRICT,

    CONSTRAINT uq_settlement_record_external_id
        UNIQUE (settlement_record_id),

    CONSTRAINT uq_settlement_record_import_row
        UNIQUE (
            settlement_import_id,
            row_number
        ),

    CONSTRAINT ck_settlement_record_row_number
        CHECK (
            row_number BETWEEN 1 AND 1000
        ),

    CONSTRAINT ck_settlement_record_external_id
        CHECK (
            settlement_record_id
                ~ '^[A-Za-z0-9][A-Za-z0-9._:-]{0,127}$'
        ),

    CONSTRAINT ck_settlement_record_amount
        CHECK (amount_minor_units > 0),

    CONSTRAINT ck_settlement_record_currency
        CHECK (currency = 'GBP')
);

CREATE INDEX idx_settlement_record_import
    ON settlement_record (
        settlement_import_id,
        row_number
    );

CREATE INDEX idx_settlement_record_payment
    ON settlement_record (
        payment_id,
        id
    );

CREATE OR REPLACE FUNCTION
    protect_settlement_import_mutation()
RETURNS TRIGGER
LANGUAGE plpgsql
AS $$
DECLARE
    stored_record_count INTEGER;
BEGIN
    IF TG_OP = 'DELETE' THEN
        RAISE EXCEPTION
            USING
                ERRCODE = '55000',
                MESSAGE =
                    'settlement_import cannot be deleted';
    END IF;

    IF OLD.status = 'COMPLETED' THEN
        RAISE EXCEPTION
            USING
                ERRCODE = '55000',
                MESSAGE =
                    'completed settlement_import is immutable';
    END IF;

    IF NEW.id IS DISTINCT FROM OLD.id
        OR NEW.raw_file_sha256
            IS DISTINCT FROM OLD.raw_file_sha256
        OR NEW.raw_file_size_bytes
            IS DISTINCT FROM OLD.raw_file_size_bytes
        OR NEW.original_filename
            IS DISTINCT FROM OLD.original_filename
        OR NEW.actor_identity_user_id
            IS DISTINCT FROM OLD.actor_identity_user_id
        OR NEW.created_at
            IS DISTINCT FROM OLD.created_at
    THEN
        RAISE EXCEPTION
            USING
                ERRCODE = '55000',
                MESSAGE =
                    'settlement_import metadata is immutable';
    END IF;

    IF NEW.status <> 'COMPLETED' THEN
        RAISE EXCEPTION
            USING
                ERRCODE = '55000',
                MESSAGE =
                    'settlement_import may only complete';
    END IF;

    IF NEW.version <> OLD.version + 1 THEN
        RAISE EXCEPTION
            USING
                ERRCODE = '55000',
                MESSAGE =
                    'settlement_import version must increment once';
    END IF;

    SELECT COUNT(*)
    INTO stored_record_count
    FROM settlement_record
    WHERE settlement_import_id = OLD.id;

    IF NEW.row_count <> stored_record_count THEN
        RAISE EXCEPTION
            USING
                ERRCODE = '23514',
                CONSTRAINT =
                    'ck_settlement_import_record_count',
                MESSAGE =
                    'settlement_import row count does not match records';
    END IF;

    RETURN NEW;
END;
$$;

CREATE TRIGGER trg_settlement_import_protected
BEFORE UPDATE OR DELETE
ON settlement_import
FOR EACH ROW
EXECUTE FUNCTION
    protect_settlement_import_mutation();

CREATE OR REPLACE FUNCTION
    require_processing_settlement_import()
RETURNS TRIGGER
LANGUAGE plpgsql
AS $$
BEGIN
    IF NOT EXISTS (
        SELECT 1
        FROM settlement_import
        WHERE id = NEW.settlement_import_id
          AND status = 'PROCESSING'
    ) THEN
        RAISE EXCEPTION
            USING
                ERRCODE = '55000',
                MESSAGE =
                    'settlement records require a processing import';
    END IF;

    RETURN NEW;
END;
$$;

CREATE TRIGGER trg_settlement_record_processing_import
BEFORE INSERT
ON settlement_record
FOR EACH ROW
EXECUTE FUNCTION
    require_processing_settlement_import();

CREATE OR REPLACE FUNCTION
    reject_settlement_record_mutation()
RETURNS TRIGGER
LANGUAGE plpgsql
AS $$
BEGIN
    RAISE EXCEPTION
        USING
            ERRCODE = '55000',
            MESSAGE =
                'settlement_record is immutable';
END;
$$;

CREATE TRIGGER trg_settlement_record_immutable
BEFORE UPDATE OR DELETE
ON settlement_record
FOR EACH ROW
EXECUTE FUNCTION
    reject_settlement_record_mutation();
