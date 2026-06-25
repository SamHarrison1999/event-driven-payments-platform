CREATE TABLE identity_security_event (
    id UUID NOT NULL,
    event_type VARCHAR(64) NOT NULL,
    actor_user_id UUID NOT NULL,
    subject_user_id UUID NOT NULL,
    role_code VARCHAR(32) NOT NULL,
    occurred_at TIMESTAMP WITH TIME ZONE NOT NULL,

    CONSTRAINT pk_identity_security_event
        PRIMARY KEY (id),

    CONSTRAINT ck_identity_security_event_type
        CHECK (
            event_type IN (
                'ROLE_GRANTED',
                'ROLE_REVOKED'
            )
        ),

    CONSTRAINT ck_identity_security_event_role
        CHECK (
            role_code IN (
                'CUSTOMER',
                'OPERATIONS',
                'RECONCILIATION_ANALYST',
                'ADMIN'
            )
        )
);

CREATE INDEX idx_identity_security_event_subject
    ON identity_security_event (
        subject_user_id,
        occurred_at
    );

CREATE INDEX idx_identity_security_event_actor
    ON identity_security_event (
        actor_user_id,
        occurred_at
    );

CREATE FUNCTION reject_identity_security_event_mutation()
RETURNS TRIGGER
LANGUAGE plpgsql
AS $$
BEGIN
    RAISE EXCEPTION
        'identity_security_event rows are immutable';
END;
$$;

CREATE TRIGGER trg_identity_security_event_immutable
BEFORE UPDATE OR DELETE
ON identity_security_event
FOR EACH ROW
EXECUTE FUNCTION
    reject_identity_security_event_mutation();
