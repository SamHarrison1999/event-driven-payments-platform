CREATE TEMP TABLE orphan_customer_onboarding (
    identity_user_id UUID PRIMARY KEY,
    customer_id UUID NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL
) ON COMMIT DROP;

INSERT INTO orphan_customer_onboarding (
    identity_user_id,
    customer_id,
    created_at
)
SELECT DISTINCT
    identity_user.id,
    gen_random_uuid(),
    CURRENT_TIMESTAMP
FROM identity_user
INNER JOIN identity_user_role
    ON identity_user_role.user_id =
       identity_user.id
LEFT JOIN customer_identity_assignment
    ON customer_identity_assignment.identity_user_id =
       identity_user.id
WHERE
    identity_user_role.role_code = 'CUSTOMER'
    AND customer_identity_assignment.identity_user_id
        IS NULL;

INSERT INTO customer_profile (
    id,
    full_name,
    status,
    created_at,
    updated_at,
    version
)
SELECT
    customer_id,
    'Demo Customer',
    'ACTIVE',
    created_at,
    created_at,
    0
FROM orphan_customer_onboarding;

INSERT INTO customer_identity_assignment (
    identity_user_id,
    customer_id,
    assigned_at,
    version
)
SELECT
    identity_user_id,
    customer_id,
    created_at,
    0
FROM orphan_customer_onboarding;

WITH customers_without_accounts AS (
    SELECT DISTINCT
        customer_identity_assignment.customer_id
    FROM customer_identity_assignment
    INNER JOIN identity_user_role
        ON identity_user_role.user_id =
           customer_identity_assignment.identity_user_id
    WHERE
        identity_user_role.role_code = 'CUSTOMER'
        AND NOT EXISTS (
            SELECT 1
            FROM customer_account
            WHERE
                customer_account.customer_id =
                customer_identity_assignment.customer_id
        )
)
INSERT INTO customer_account (
    id,
    customer_id,
    currency,
    balance_minor_units,
    status,
    created_at,
    updated_at,
    version
)
SELECT
    gen_random_uuid(),
    customers_without_accounts.customer_id,
    'GBP',
    100000,
    'ACTIVE',
    CURRENT_TIMESTAMP,
    CURRENT_TIMESTAMP,
    0
FROM customers_without_accounts
CROSS JOIN generate_series(1, 2);