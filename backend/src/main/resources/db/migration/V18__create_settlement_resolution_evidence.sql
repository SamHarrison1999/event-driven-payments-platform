CREATE TABLE settlement_resolution (
    id UUID NOT NULL,
    settlement_discrepancy_id UUID NOT NULL,
    actor_identity_user_id UUID NOT NULL,
    decision VARCHAR(48) NOT NULL,
    reason VARCHAR(500) NOT NULL,
    discrepancy_version BIGINT NOT NULL,
    decided_at TIMESTAMP WITH TIME ZONE NOT NULL,

    CONSTRAINT pk_settlement_resolution
        PRIMARY KEY (id),

    CONSTRAINT fk_settlement_resolution_discrepancy
        FOREIGN KEY (settlement_discrepancy_id)
        REFERENCES settlement_discrepancy (id)
        ON DELETE RESTRICT,

    CONSTRAINT fk_settlement_resolution_actor
        FOREIGN KEY (actor_identity_user_id)
        REFERENCES identity_user (id)
        ON DELETE RESTRICT,

    CONSTRAINT uq_settlement_resolution_discrepancy
        UNIQUE (settlement_discrepancy_id),

    CONSTRAINT ck_settlement_resolution_decision
        CHECK (
            decision IN (
                'ACCEPTED',
                'INTERNAL_CORRECTION_REQUIRED',
                'EXTERNAL_CORRECTION_REQUIRED'
            )
        ),

    CONSTRAINT ck_settlement_resolution_reason
        CHECK (
            reason = BTRIM(reason)
            AND CHAR_LENGTH(reason) BETWEEN 1 AND 500
            AND reason !~ '[[:cntrl:]]'
        ),

    CONSTRAINT ck_settlement_resolution_version
        CHECK (discrepancy_version >= 0)
);

CREATE OR REPLACE FUNCTION
    validate_settlement_resolution_insert()
RETURNS TRIGGER
LANGUAGE plpgsql
AS $$
DECLARE
    source_status VARCHAR(16);
    source_version BIGINT;
    source_created_at TIMESTAMP WITH TIME ZONE;
BEGIN
    SELECT
        status,
        version,
        created_at
    INTO
        source_status,
        source_version,
        source_created_at
    FROM settlement_discrepancy
    WHERE id = NEW.settlement_discrepancy_id;

    IF source_status IS DISTINCT FROM 'OPEN'
        OR source_version IS DISTINCT FROM
            NEW.discrepancy_version
        OR NEW.decided_at < source_created_at
    THEN
        RAISE EXCEPTION
            USING
                ERRCODE = '23514',
                CONSTRAINT =
                    'ck_settlement_resolution_source',
                MESSAGE =
                    'settlement resolution does not '
                    || 'match the open discrepancy';
    END IF;

    RETURN NEW;
END;
$$;

CREATE TRIGGER trg_settlement_resolution_source
BEFORE INSERT
ON settlement_resolution
FOR EACH ROW
EXECUTE FUNCTION
    validate_settlement_resolution_insert();

CREATE TRIGGER trg_settlement_resolution_immutable
BEFORE UPDATE OR DELETE
ON settlement_resolution
FOR EACH ROW
EXECUTE FUNCTION
    reject_reconciliation_evidence_mutation();

DROP TRIGGER trg_settlement_discrepancy_immutable
ON settlement_discrepancy;

CREATE OR REPLACE FUNCTION
    protect_settlement_discrepancy_mutation()
RETURNS TRIGGER
LANGUAGE plpgsql
AS $$
BEGIN
    IF TG_OP = 'DELETE' THEN
        RAISE EXCEPTION
            USING
                ERRCODE = '55000',
                MESSAGE =
                    'settlement_discrepancy cannot be '
                    || 'deleted';
    END IF;

    IF NEW.id IS DISTINCT FROM OLD.id
        OR NEW.settlement_import_id
            IS DISTINCT FROM OLD.settlement_import_id
        OR NEW.settlement_result_id
            IS DISTINCT FROM OLD.settlement_result_id
        OR NEW.settlement_record_id
            IS DISTINCT FROM OLD.settlement_record_id
        OR NEW.code IS DISTINCT FROM OLD.code
        OR NEW.created_at
            IS DISTINCT FROM OLD.created_at
    THEN
        RAISE EXCEPTION
            USING
                ERRCODE = '55000',
                MESSAGE =
                    'settlement discrepancy evidence '
                    || 'is immutable';
    END IF;

    IF OLD.status IS DISTINCT FROM 'OPEN'
        OR NEW.status IS DISTINCT FROM 'RESOLVED'
    THEN
        RAISE EXCEPTION
            USING
                ERRCODE = '55000',
                MESSAGE =
                    'settlement discrepancy may only '
                    || 'resolve once';
    END IF;

    IF NEW.version IS DISTINCT FROM OLD.version + 1
    THEN
        RAISE EXCEPTION
            USING
                ERRCODE = '55000',
                MESSAGE =
                    'settlement discrepancy version '
                    || 'must increment once';
    END IF;

    IF NOT EXISTS (
        SELECT 1
        FROM settlement_resolution
        WHERE settlement_discrepancy_id = OLD.id
          AND discrepancy_version = OLD.version
    ) THEN
        RAISE EXCEPTION
            USING
                ERRCODE = '23514',
                CONSTRAINT =
                    'ck_settlement_discrepancy_resolution',
                MESSAGE =
                    'settlement discrepancy requires '
                    || 'matching resolution evidence';
    END IF;

    RETURN NEW;
END;
$$;

CREATE TRIGGER trg_settlement_discrepancy_lifecycle
BEFORE UPDATE OR DELETE
ON settlement_discrepancy
FOR EACH ROW
EXECUTE FUNCTION
    protect_settlement_discrepancy_mutation();

CREATE OR REPLACE FUNCTION
    require_resolved_settlement_discrepancy()
RETURNS TRIGGER
LANGUAGE plpgsql
AS $$
BEGIN
    IF NOT EXISTS (
        SELECT 1
        FROM settlement_discrepancy
        WHERE id = NEW.settlement_discrepancy_id
          AND status = 'RESOLVED'
          AND version = NEW.discrepancy_version + 1
    ) THEN
        RAISE EXCEPTION
            USING
                ERRCODE = '23514',
                CONSTRAINT =
                    'ck_settlement_resolution_completed',
                MESSAGE =
                    'settlement resolution must complete '
                    || 'its discrepancy transition';
    END IF;

    RETURN NULL;
END;
$$;

CREATE CONSTRAINT TRIGGER
    trg_settlement_resolution_completed
AFTER INSERT
ON settlement_resolution
DEFERRABLE INITIALLY DEFERRED
FOR EACH ROW
EXECUTE FUNCTION
    require_resolved_settlement_discrepancy();
