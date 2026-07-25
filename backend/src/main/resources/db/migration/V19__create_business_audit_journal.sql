CREATE FUNCTION business_audit_has_exact_keys(
    metadata JSONB,
    expected_keys TEXT[]
)
RETURNS BOOLEAN
LANGUAGE SQL
IMMUTABLE
PARALLEL SAFE
RETURN
    (
        SELECT COUNT(*)
        FROM JSONB_OBJECT_KEYS(metadata)
    ) = CARDINALITY(expected_keys)
    AND metadata ?& expected_keys;

CREATE FUNCTION business_audit_has_safe_string(
    metadata JSONB,
    key_name TEXT
)
RETURNS BOOLEAN
LANGUAGE SQL
IMMUTABLE
PARALLEL SAFE
RETURN
    JSONB_TYPEOF(metadata -> key_name) = 'string'
    AND metadata ->> key_name
        ~ '^[A-Za-z0-9][A-Za-z0-9._:-]{0,127}$';

CREATE FUNCTION business_audit_integer_between(
    metadata JSONB,
    key_name TEXT,
    minimum_value NUMERIC,
    maximum_value NUMERIC
)
RETURNS BOOLEAN
LANGUAGE plpgsql
IMMUTABLE
PARALLEL SAFE
AS $$
BEGIN
    IF JSONB_TYPEOF(metadata -> key_name)
        IS DISTINCT FROM 'number'
        OR metadata ->> key_name
            !~ '^(0|[1-9][0-9]*)$'
    THEN
        RETURN FALSE;
    END IF;

    RETURN (metadata ->> key_name)::NUMERIC
        BETWEEN minimum_value AND maximum_value;
EXCEPTION
    WHEN OTHERS THEN
        RETURN FALSE;
END;
$$;

CREATE FUNCTION validate_business_audit_metadata(
    event_code VARCHAR,
    event_schema_version INTEGER,
    metadata_text TEXT
)
RETURNS BOOLEAN
LANGUAGE plpgsql
IMMUTABLE
PARALLEL SAFE
AS $$
DECLARE
    metadata JSONB;
BEGIN
    IF event_schema_version <> 1
        OR NOT (
            metadata_text
                IS JSON OBJECT WITH UNIQUE KEYS
        )
    THEN
        RETURN FALSE;
    END IF;

    metadata := metadata_text::JSONB;

    CASE event_code
        WHEN 'customer.created' THEN
            RETURN business_audit_has_exact_keys(
                metadata,
                ARRAY['status']
            )
            AND metadata ->> 'status' = 'ACTIVE';

        WHEN 'customer.status-changed' THEN
            RETURN business_audit_has_exact_keys(
                metadata,
                ARRAY[
                    'previousStatus',
                    'newStatus'
                ]
            )
            AND metadata ->> 'previousStatus'
                IN ('ACTIVE', 'SUSPENDED', 'CLOSED')
            AND metadata ->> 'newStatus'
                IN ('ACTIVE', 'SUSPENDED', 'CLOSED')
            AND metadata ->> 'previousStatus'
                <> metadata ->> 'newStatus';

        WHEN 'account.created' THEN
            RETURN business_audit_has_exact_keys(
                metadata,
                ARRAY[
                    'customerId',
                    'currency',
                    'status'
                ]
            )
            AND business_audit_has_safe_string(
                metadata,
                'customerId'
            )
            AND metadata ->> 'currency' = 'GBP'
            AND metadata ->> 'status' = 'ACTIVE';

        WHEN 'account.status-changed' THEN
            RETURN business_audit_has_exact_keys(
                metadata,
                ARRAY[
                    'previousStatus',
                    'newStatus'
                ]
            )
            AND metadata ->> 'previousStatus'
                IN ('ACTIVE', 'FROZEN', 'CLOSED')
            AND metadata ->> 'newStatus'
                IN ('ACTIVE', 'FROZEN', 'CLOSED')
            AND metadata ->> 'previousStatus'
                <> metadata ->> 'newStatus';

        WHEN 'customer.identity-assigned' THEN
            RETURN business_audit_has_exact_keys(
                metadata,
                ARRAY['customerId']
            )
            AND business_audit_has_safe_string(
                metadata,
                'customerId'
            );

        WHEN 'payment.submitted' THEN
            RETURN business_audit_has_exact_keys(
                metadata,
                ARRAY[
                    'amountMinor',
                    'currency',
                    'destinationAccountId',
                    'sourceAccountId'
                ]
            )
            AND business_audit_integer_between(
                metadata,
                'amountMinor',
                1,
                9223372036854775807
            )
            AND metadata ->> 'currency' = 'GBP'
            AND business_audit_has_safe_string(
                metadata,
                'destinationAccountId'
            )
            AND business_audit_has_safe_string(
                metadata,
                'sourceAccountId'
            );

        WHEN 'payment.completed' THEN
            RETURN business_audit_has_exact_keys(
                metadata,
                ARRAY['amountMinor', 'currency']
            )
            AND business_audit_integer_between(
                metadata,
                'amountMinor',
                1,
                9223372036854775807
            )
            AND metadata ->> 'currency' = 'GBP';

        WHEN 'payment.rejected' THEN
            RETURN business_audit_has_exact_keys(
                metadata,
                ARRAY['reasonCode']
            )
            AND metadata ->> 'reasonCode' IN (
                'PAYMENT_SOURCE_NOT_OWNED',
                'PAYMENT_SOURCE_NOT_FOUND',
                'PAYMENT_DESTINATION_NOT_FOUND',
                'PAYMENT_SOURCE_NOT_ACTIVE',
                'PAYMENT_DESTINATION_NOT_ACTIVE',
                'PAYMENT_CURRENCY_MISMATCH',
                'PAYMENT_INSUFFICIENT_FUNDS'
            );

        WHEN 'payment.failed' THEN
            RETURN business_audit_has_exact_keys(
                metadata,
                ARRAY['failureCode']
            )
            AND metadata ->> 'failureCode' IN (
                'PAYMENT_PROCESSING_FAILED',
                'PAYMENT_CONCURRENT_MODIFICATION'
            );

        WHEN 'settlement.import-accepted' THEN
            RETURN business_audit_has_exact_keys(
                metadata,
                ARRAY[
                    'discrepancyCount',
                    'matchedCount',
                    'rowCount'
                ]
            )
            AND business_audit_integer_between(
                metadata,
                'discrepancyCount',
                0,
                2147483647
            )
            AND business_audit_integer_between(
                metadata,
                'matchedCount',
                0,
                2147483647
            )
            AND business_audit_integer_between(
                metadata,
                'rowCount',
                1,
                2147483647
            )
            AND (metadata ->> 'rowCount')::NUMERIC
                = (metadata ->> 'matchedCount')::NUMERIC
                    + (
                        metadata ->> 'discrepancyCount'
                    )::NUMERIC;

        ELSE
            RETURN FALSE;
    END CASE;
EXCEPTION
    WHEN OTHERS THEN
        RETURN FALSE;
END;
$$;

CREATE TABLE business_audit_event (
    id UUID NOT NULL,
    event_type VARCHAR(64) NOT NULL,
    schema_version INTEGER NOT NULL,
    occurred_at TIMESTAMP WITH TIME ZONE NOT NULL,
    recorded_at TIMESTAMP WITH TIME ZONE NOT NULL,
    actor_kind VARCHAR(32) NOT NULL,
    actor_identity_user_id UUID,
    subject_type VARCHAR(64) NOT NULL,
    subject_identifier VARCHAR(128) NOT NULL,
    source_module VARCHAR(64) NOT NULL,
    source_record_type VARCHAR(64) NOT NULL,
    source_record_identifier VARCHAR(128) NOT NULL,
    source_event_identifier VARCHAR(128) NOT NULL,
    correlation_identifier VARCHAR(128) NOT NULL,
    metadata TEXT NOT NULL,

    CONSTRAINT pk_business_audit_event
        PRIMARY KEY (id),

    CONSTRAINT uq_business_audit_event_source
        UNIQUE (
            source_module,
            event_type,
            source_record_type,
            source_record_identifier,
            source_event_identifier
        ),

    CONSTRAINT ck_business_audit_event_type
        CHECK (
            event_type IN (
                'customer.created',
                'customer.status-changed',
                'account.created',
                'account.status-changed',
                'customer.identity-assigned',
                'payment.submitted',
                'payment.completed',
                'payment.rejected',
                'payment.failed',
                'settlement.import-accepted'
            )
        ),

    CONSTRAINT ck_business_audit_event_schema
        CHECK (schema_version = 1),

    CONSTRAINT ck_business_audit_event_time
        CHECK (recorded_at >= occurred_at),

    CONSTRAINT ck_business_audit_event_actor
        CHECK (
            (
                actor_kind = 'IDENTITY_USER'
                AND actor_identity_user_id IS NOT NULL
            )
            OR (
                actor_kind = 'SYSTEM'
                AND actor_identity_user_id IS NULL
            )
        ),

    CONSTRAINT ck_business_audit_event_subject_type
        CHECK (
            subject_type
                ~ '^[a-z][a-z0-9_]{0,63}$'
        ),

    CONSTRAINT ck_business_audit_event_subject_id
        CHECK (
            subject_identifier
                ~ '^[A-Za-z0-9][A-Za-z0-9._:-]{0,127}$'
        ),

    CONSTRAINT ck_business_audit_event_source_module
        CHECK (
            source_module
                ~ '^[a-z][a-z0-9_]{0,63}$'
        ),

    CONSTRAINT ck_business_audit_event_source_type
        CHECK (
            source_record_type
                ~ '^[a-z][a-z0-9_]{0,63}$'
        ),

    CONSTRAINT ck_business_audit_event_source_record
        CHECK (
            source_record_identifier
                ~ '^[A-Za-z0-9][A-Za-z0-9._:-]{0,127}$'
        ),

    CONSTRAINT ck_business_audit_event_source_event
        CHECK (
            source_event_identifier
                ~ '^[A-Za-z0-9][A-Za-z0-9._:-]{0,127}$'
        ),

    CONSTRAINT ck_business_audit_event_correlation
        CHECK (
            correlation_identifier
                ~ '^[A-Za-z0-9][A-Za-z0-9._-]{0,127}$'
        ),

    CONSTRAINT ck_business_audit_event_ownership
        CHECK (
            (
                event_type IN (
                    'customer.created',
                    'customer.status-changed'
                )
                AND source_module = 'customer'
                AND source_record_type = 'customer'
                AND subject_type = 'customer'
            )
            OR (
                event_type IN (
                    'account.created',
                    'account.status-changed'
                )
                AND source_module = 'account'
                AND source_record_type = 'account'
                AND subject_type = 'account'
            )
            OR (
                event_type =
                    'customer.identity-assigned'
                AND source_module = 'customer'
                AND source_record_type =
                    'customer_identity_assignment'
                AND subject_type = 'customer'
            )
            OR (
                event_type IN (
                    'payment.submitted',
                    'payment.completed',
                    'payment.rejected',
                    'payment.failed'
                )
                AND source_module = 'payment'
                AND source_record_type = 'payment'
                AND subject_type = 'payment'
            )
            OR (
                event_type =
                    'settlement.import-accepted'
                AND source_module = 'reconciliation'
                AND source_record_type =
                    'settlement_import'
                AND subject_type =
                    'settlement_import'
            )
        ),

    CONSTRAINT ck_business_audit_event_source_subject
        CHECK (
            (
                event_type =
                    'customer.identity-assigned'
                AND metadata::JSONB
                    ->> 'customerId'
                    = subject_identifier
            )
            OR (
                event_type <>
                    'customer.identity-assigned'
                AND source_record_identifier
                    = subject_identifier
            )
        ),

    CONSTRAINT ck_business_audit_event_metadata_size
        CHECK (
            OCTET_LENGTH(metadata) <= 4096
        ),

    CONSTRAINT ck_business_audit_event_metadata
        CHECK (
            validate_business_audit_metadata(
                event_type,
                schema_version,
                metadata
            )
        )
);

CREATE INDEX idx_business_audit_event_time
    ON business_audit_event (
        occurred_at DESC,
        id DESC
    );

CREATE INDEX idx_business_audit_event_subject
    ON business_audit_event (
        subject_type,
        subject_identifier,
        occurred_at DESC,
        id DESC
    );

CREATE INDEX idx_business_audit_event_actor
    ON business_audit_event (
        actor_identity_user_id,
        occurred_at DESC,
        id DESC
    )
    WHERE actor_identity_user_id IS NOT NULL;

CREATE INDEX idx_business_audit_event_correlation
    ON business_audit_event (
        correlation_identifier,
        occurred_at DESC,
        id DESC
    );

CREATE FUNCTION reject_business_audit_event_mutation()
RETURNS TRIGGER
LANGUAGE plpgsql
AS $$
BEGIN
    RAISE EXCEPTION
        USING
            ERRCODE = '55000',
            MESSAGE =
                'business_audit_event is immutable';
END;
$$;

CREATE TRIGGER trg_business_audit_event_immutable
BEFORE UPDATE OR DELETE
ON business_audit_event
FOR EACH ROW
EXECUTE FUNCTION
    reject_business_audit_event_mutation();
