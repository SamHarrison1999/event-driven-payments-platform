CREATE TABLE settlement_result (
    id UUID NOT NULL,
    settlement_import_id UUID NOT NULL,
    settlement_record_id UUID NOT NULL,
    row_number INTEGER NOT NULL,
    outcome VARCHAR(16) NOT NULL,
    discrepancy_code VARCHAR(64),
    reconciled_at TIMESTAMP WITH TIME ZONE NOT NULL,

    CONSTRAINT pk_settlement_result
        PRIMARY KEY (id),

    CONSTRAINT fk_settlement_result_import
        FOREIGN KEY (settlement_import_id)
        REFERENCES settlement_import (id)
        ON DELETE RESTRICT,

    CONSTRAINT fk_settlement_result_record
        FOREIGN KEY (settlement_record_id)
        REFERENCES settlement_record (id)
        ON DELETE RESTRICT,

    CONSTRAINT uq_settlement_result_record
        UNIQUE (settlement_record_id),

    CONSTRAINT uq_settlement_result_import_row
        UNIQUE (
            settlement_import_id,
            row_number
        ),

    CONSTRAINT ck_settlement_result_row
        CHECK (row_number BETWEEN 1 AND 1000),

    CONSTRAINT ck_settlement_result_outcome
        CHECK (
            outcome IN (
                'MATCHED',
                'DISCREPANCY'
            )
        ),

    CONSTRAINT ck_settlement_result_code
        CHECK (
            (
                outcome = 'MATCHED'
                AND discrepancy_code IS NULL
            )
            OR (
                outcome = 'DISCREPANCY'
                AND discrepancy_code IN (
                    'PAYMENT_NOT_FOUND',
                    'PAYMENT_NOT_COMPLETED',
                    'CURRENCY_MISMATCH',
                    'AMOUNT_MISMATCH',
                    'SETTLED_BEFORE_COMPLETION',
                    'DUPLICATE_PAYMENT_SETTLEMENT'
                )
            )
        )
);

CREATE INDEX idx_settlement_result_import
    ON settlement_result (
        settlement_import_id,
        row_number
    );

CREATE INDEX idx_settlement_result_outcome
    ON settlement_result (
        settlement_import_id,
        outcome,
        row_number
    );

CREATE TABLE settlement_match_claim (
    payment_id UUID NOT NULL,
    settlement_record_id UUID NOT NULL,
    claimed_at TIMESTAMP WITH TIME ZONE NOT NULL,

    CONSTRAINT pk_settlement_match_claim
        PRIMARY KEY (payment_id),

    CONSTRAINT fk_settlement_match_claim_record
        FOREIGN KEY (settlement_record_id)
        REFERENCES settlement_record (id)
        ON DELETE RESTRICT,

    CONSTRAINT uq_settlement_match_claim_record
        UNIQUE (settlement_record_id)
);

CREATE TABLE settlement_discrepancy (
    id UUID NOT NULL,
    settlement_import_id UUID NOT NULL,
    settlement_result_id UUID NOT NULL,
    settlement_record_id UUID NOT NULL,
    code VARCHAR(64) NOT NULL,
    status VARCHAR(16) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    version BIGINT NOT NULL,

    CONSTRAINT pk_settlement_discrepancy
        PRIMARY KEY (id),

    CONSTRAINT fk_settlement_discrepancy_import
        FOREIGN KEY (settlement_import_id)
        REFERENCES settlement_import (id)
        ON DELETE RESTRICT,

    CONSTRAINT fk_settlement_discrepancy_result
        FOREIGN KEY (settlement_result_id)
        REFERENCES settlement_result (id)
        ON DELETE RESTRICT,

    CONSTRAINT fk_settlement_discrepancy_record
        FOREIGN KEY (settlement_record_id)
        REFERENCES settlement_record (id)
        ON DELETE RESTRICT,

    CONSTRAINT uq_settlement_discrepancy_result
        UNIQUE (settlement_result_id),

    CONSTRAINT uq_settlement_discrepancy_record
        UNIQUE (settlement_record_id),

    CONSTRAINT ck_settlement_discrepancy_code
        CHECK (
            code IN (
                'PAYMENT_NOT_FOUND',
                'PAYMENT_NOT_COMPLETED',
                'CURRENCY_MISMATCH',
                'AMOUNT_MISMATCH',
                'SETTLED_BEFORE_COMPLETION',
                'DUPLICATE_PAYMENT_SETTLEMENT'
            )
        ),

    CONSTRAINT ck_settlement_discrepancy_status
        CHECK (
            status IN (
                'OPEN',
                'RESOLVED'
            )
        ),

    CONSTRAINT ck_settlement_discrepancy_version
        CHECK (version >= 0)
);

CREATE INDEX idx_settlement_discrepancy_queue
    ON settlement_discrepancy (
        status,
        created_at,
        id
    );

CREATE INDEX idx_settlement_discrepancy_import
    ON settlement_discrepancy (
        settlement_import_id,
        id
    );

CREATE OR REPLACE FUNCTION
    require_processing_reconciliation_import()
RETURNS TRIGGER
LANGUAGE plpgsql
AS $$
DECLARE
    target_import_id UUID;
    source_import_id UUID;
    source_row_number INTEGER;
    source_payment_id UUID;
    source_record_id UUID;
    source_outcome VARCHAR(16);
    source_code VARCHAR(64);
BEGIN
    IF TG_TABLE_NAME = 'settlement_result' THEN
        SELECT
            settlement_import_id,
            row_number
        INTO
            source_import_id,
            source_row_number
        FROM settlement_record
        WHERE id = NEW.settlement_record_id;

        IF source_import_id IS DISTINCT FROM
                NEW.settlement_import_id
            OR source_row_number IS DISTINCT FROM
                NEW.row_number
        THEN
            RAISE EXCEPTION
                USING
                    ERRCODE = '23514',
                    CONSTRAINT =
                        'ck_settlement_result_source',
                    MESSAGE =
                        'settlement result source does '
                        || 'not match its import and row';
        END IF;

        target_import_id := NEW.settlement_import_id;
    ELSIF TG_TABLE_NAME = 'settlement_match_claim' THEN
        SELECT
            settlement_import_id,
            payment_id
        INTO
            target_import_id,
            source_payment_id
        FROM settlement_record
        WHERE id = NEW.settlement_record_id;

        IF source_payment_id IS DISTINCT FROM
                NEW.payment_id
        THEN
            RAISE EXCEPTION
                USING
                    ERRCODE = '23514',
                    CONSTRAINT =
                        'ck_settlement_match_claim_payment',
                    MESSAGE =
                        'settlement match claim payment '
                        || 'does not match its record';
        END IF;
    ELSIF TG_TABLE_NAME = 'settlement_discrepancy' THEN
        SELECT
            settlement_import_id,
            settlement_record_id,
            outcome,
            discrepancy_code
        INTO
            source_import_id,
            source_record_id,
            source_outcome,
            source_code
        FROM settlement_result
        WHERE id = NEW.settlement_result_id;

        IF source_import_id IS DISTINCT FROM
                NEW.settlement_import_id
            OR source_record_id IS DISTINCT FROM
                NEW.settlement_record_id
            OR source_outcome IS DISTINCT FROM
                'DISCREPANCY'
            OR source_code IS DISTINCT FROM NEW.code
            OR NEW.status IS DISTINCT FROM 'OPEN'
            OR NEW.version IS DISTINCT FROM 0
        THEN
            RAISE EXCEPTION
                USING
                    ERRCODE = '23514',
                    CONSTRAINT =
                        'ck_settlement_discrepancy_source',
                    MESSAGE =
                        'settlement discrepancy does not '
                        || 'match its result';
        END IF;

        target_import_id := NEW.settlement_import_id;
    ELSE
        RAISE EXCEPTION
            USING
                ERRCODE = '55000',
                MESSAGE =
                    'unexpected reconciliation table';
    END IF;

    IF NOT EXISTS (
        SELECT 1
        FROM settlement_import
        WHERE id = target_import_id
          AND status = 'PROCESSING'
    ) THEN
        RAISE EXCEPTION
            USING
                ERRCODE = '55000',
                MESSAGE =
                    'reconciliation evidence requires '
                    || 'a processing import';
    END IF;

    RETURN NEW;
END;
$$;

CREATE TRIGGER trg_settlement_result_processing_import
BEFORE INSERT
ON settlement_result
FOR EACH ROW
EXECUTE FUNCTION
    require_processing_reconciliation_import();

CREATE TRIGGER trg_settlement_claim_processing_import
BEFORE INSERT
ON settlement_match_claim
FOR EACH ROW
EXECUTE FUNCTION
    require_processing_reconciliation_import();

CREATE TRIGGER trg_settlement_discrepancy_processing_import
BEFORE INSERT
ON settlement_discrepancy
FOR EACH ROW
EXECUTE FUNCTION
    require_processing_reconciliation_import();

CREATE OR REPLACE FUNCTION
    reject_reconciliation_evidence_mutation()
RETURNS TRIGGER
LANGUAGE plpgsql
AS $$
BEGIN
    RAISE EXCEPTION
        USING
            ERRCODE = '55000',
            MESSAGE =
                TG_TABLE_NAME || ' is immutable';
END;
$$;

CREATE TRIGGER trg_settlement_result_immutable
BEFORE UPDATE OR DELETE
ON settlement_result
FOR EACH ROW
EXECUTE FUNCTION
    reject_reconciliation_evidence_mutation();

CREATE TRIGGER trg_settlement_claim_immutable
BEFORE UPDATE OR DELETE
ON settlement_match_claim
FOR EACH ROW
EXECUTE FUNCTION
    reject_reconciliation_evidence_mutation();

CREATE TRIGGER trg_settlement_discrepancy_immutable
BEFORE UPDATE OR DELETE
ON settlement_discrepancy
FOR EACH ROW
EXECUTE FUNCTION
    reject_reconciliation_evidence_mutation();

CREATE OR REPLACE FUNCTION
    protect_settlement_import_mutation()
RETURNS TRIGGER
LANGUAGE plpgsql
AS $$
DECLARE
    stored_record_count INTEGER;
    stored_result_count INTEGER;
    stored_matched_count INTEGER;
    stored_discrepancy_result_count INTEGER;
    stored_discrepancy_count INTEGER;
    matched_without_claim_count INTEGER;
    unmatched_with_claim_count INTEGER;
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

    SELECT
        COUNT(*),
        COUNT(*) FILTER (
            WHERE outcome = 'MATCHED'
        ),
        COUNT(*) FILTER (
            WHERE outcome = 'DISCREPANCY'
        )
    INTO
        stored_result_count,
        stored_matched_count,
        stored_discrepancy_result_count
    FROM settlement_result
    WHERE settlement_import_id = OLD.id;

    SELECT COUNT(*)
    INTO stored_discrepancy_count
    FROM settlement_discrepancy
    WHERE settlement_import_id = OLD.id;

    SELECT COUNT(*)
    INTO matched_without_claim_count
    FROM settlement_result result
    LEFT JOIN settlement_match_claim claim
      ON claim.settlement_record_id =
            result.settlement_record_id
    WHERE result.settlement_import_id = OLD.id
      AND result.outcome = 'MATCHED'
      AND claim.payment_id IS NULL;

    SELECT COUNT(*)
    INTO unmatched_with_claim_count
    FROM settlement_result result
    JOIN settlement_match_claim claim
      ON claim.settlement_record_id =
            result.settlement_record_id
    WHERE result.settlement_import_id = OLD.id
      AND result.outcome <> 'MATCHED';

    IF NEW.row_count <> stored_record_count
        OR NEW.row_count <> stored_result_count
    THEN
        RAISE EXCEPTION
            USING
                ERRCODE = '23514',
                CONSTRAINT =
                    'ck_settlement_import_evidence_count',
                MESSAGE =
                    'settlement import row and result '
                    || 'counts do not match';
    END IF;

    IF NEW.matched_count <> stored_matched_count
        OR NEW.discrepancy_count
            <> stored_discrepancy_result_count
        OR NEW.discrepancy_count
            <> stored_discrepancy_count
    THEN
        RAISE EXCEPTION
            USING
                ERRCODE = '23514',
                CONSTRAINT =
                    'ck_settlement_import_outcome_count',
                MESSAGE =
                    'settlement import outcome counts '
                    || 'do not match evidence';
    END IF;

    IF matched_without_claim_count <> 0
        OR unmatched_with_claim_count <> 0
    THEN
        RAISE EXCEPTION
            USING
                ERRCODE = '23514',
                CONSTRAINT =
                    'ck_settlement_import_match_claims',
                MESSAGE =
                    'settlement match claims do not '
                    || 'match results';
    END IF;

    RETURN NEW;
END;
$$;
