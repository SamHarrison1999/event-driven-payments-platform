CREATE FUNCTION reject_posted_ledger_mutation()
RETURNS TRIGGER
LANGUAGE plpgsql
AS $$
BEGIN
    RAISE EXCEPTION
        USING
            ERRCODE = '55000',
            MESSAGE = FORMAT(
                'Posted ledger records are immutable: %s on %s is not permitted.',
                TG_OP,
                TG_TABLE_NAME
            );
END;
$$;

CREATE TRIGGER trg_ledger_transaction_immutable
BEFORE UPDATE OR DELETE
ON ledger_transaction
FOR EACH ROW
EXECUTE FUNCTION reject_posted_ledger_mutation();

CREATE TRIGGER trg_ledger_entry_immutable
BEFORE UPDATE OR DELETE
ON ledger_entry
FOR EACH ROW
EXECUTE FUNCTION reject_posted_ledger_mutation();

CREATE FUNCTION assert_ledger_transaction_balanced()
RETURNS TRIGGER
LANGUAGE plpgsql
AS $$
DECLARE
    checked_transaction_id UUID;
    entry_count BIGINT;
    debit_count BIGINT;
    credit_count BIGINT;
    debit_total NUMERIC;
    credit_total NUMERIC;
BEGIN
    IF TG_TABLE_NAME = 'ledger_transaction' THEN
        checked_transaction_id := NEW.id;
    ELSE
        checked_transaction_id := NEW.transaction_id;
    END IF;

    SELECT
        COUNT(*),
        COUNT(*) FILTER (
            WHERE side = 'DEBIT'
        ),
        COUNT(*) FILTER (
            WHERE side = 'CREDIT'
        ),
        COALESCE(
            SUM(amount_minor_units) FILTER (
                WHERE side = 'DEBIT'
            ),
            0
        ),
        COALESCE(
            SUM(amount_minor_units) FILTER (
                WHERE side = 'CREDIT'
            ),
            0
        )
    INTO
        entry_count,
        debit_count,
        credit_count,
        debit_total,
        credit_total
    FROM ledger_entry
    WHERE transaction_id = checked_transaction_id;

    IF entry_count < 2 THEN
        RAISE EXCEPTION
            USING
                ERRCODE = '23514',
                CONSTRAINT =
                    'ck_ledger_transaction_balanced',
                MESSAGE = FORMAT(
                    'Ledger transaction %s must contain at least two entries.',
                    checked_transaction_id
                );
    END IF;

    IF debit_count < 1 OR credit_count < 1 THEN
        RAISE EXCEPTION
            USING
                ERRCODE = '23514',
                CONSTRAINT =
                    'ck_ledger_transaction_balanced',
                MESSAGE = FORMAT(
                    'Ledger transaction %s must contain at least one debit and one credit.',
                    checked_transaction_id
                );
    END IF;

    IF debit_total <> credit_total THEN
        RAISE EXCEPTION
            USING
                ERRCODE = '23514',
                CONSTRAINT =
                    'ck_ledger_transaction_balanced',
                MESSAGE = FORMAT(
                    'Ledger transaction %s is unbalanced: debits=%s, credits=%s.',
                    checked_transaction_id,
                    debit_total,
                    credit_total
                );
    END IF;

    RETURN NULL;
END;
$$;

CREATE CONSTRAINT TRIGGER ct_ledger_transaction_balanced
AFTER INSERT
ON ledger_transaction
DEFERRABLE INITIALLY DEFERRED
FOR EACH ROW
EXECUTE FUNCTION assert_ledger_transaction_balanced();

CREATE CONSTRAINT TRIGGER ct_ledger_entry_balanced
AFTER INSERT
ON ledger_entry
DEFERRABLE INITIALLY DEFERRED
FOR EACH ROW
EXECUTE FUNCTION assert_ledger_transaction_balanced();