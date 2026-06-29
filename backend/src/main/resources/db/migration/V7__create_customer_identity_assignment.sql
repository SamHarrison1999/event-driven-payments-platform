CREATE TABLE customer_identity_assignment (
    identity_user_id UUID NOT NULL,
    customer_id UUID NOT NULL,
    assigned_at TIMESTAMP WITH TIME ZONE NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,

    CONSTRAINT pk_customer_identity_assignment
        PRIMARY KEY (identity_user_id),

    CONSTRAINT fk_customer_identity_assignment_user
        FOREIGN KEY (identity_user_id)
        REFERENCES identity_user (id)
        ON DELETE CASCADE,

    CONSTRAINT fk_customer_identity_assignment_customer
        FOREIGN KEY (customer_id)
        REFERENCES customer_profile (id)
        ON DELETE RESTRICT,

    CONSTRAINT ck_customer_identity_assignment_version
        CHECK (version >= 0)
);

CREATE INDEX idx_customer_identity_assignment_customer
    ON customer_identity_assignment (customer_id);