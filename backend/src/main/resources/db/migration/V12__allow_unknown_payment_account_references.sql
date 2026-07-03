-- A payment records the account identifiers supplied by the caller even when
-- later account lookup deterministically rejects one of those identifiers.
-- Ledger entries continue to require existing customer accounts.

ALTER TABLE payment
    DROP CONSTRAINT fk_payment_source_account;

ALTER TABLE payment
    DROP CONSTRAINT fk_payment_destination_account;
