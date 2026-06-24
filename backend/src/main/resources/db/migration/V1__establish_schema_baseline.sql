-- Establishes the initial Flyway-controlled database baseline.
--
-- No domain tables are created until their owning modules are implemented.
-- This migration verifies the migration pipeline without prematurely fixing
-- customer, account, payment or ledger schemas.

SELECT 1;
