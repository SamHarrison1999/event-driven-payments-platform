# ADR 0003: Money, ledger and consistency model

- Status: Accepted; money representation partially superseded by ADR 0008
- Date: 2026-06-24
- Decision owners: Project maintainer

ADR 0008 supersedes the money-representation and PostgreSQL monetary-storage
decisions in this record. The double-entry, balancing, immutability, balance
snapshot, concurrency and correction decisions remain accepted.

## Context

Financial correctness requires:

- exact decimal representation;
- balanced ledger transactions;
- immutable postings;
- auditable corrections; and
- protection against concurrent debits.

A balance column alone cannot explain how value moved.

A balance-only model is insufficient for reconciliation, auditing and
demonstrating financial-domain engineering.

## Decision

## Money representation

Represent money as an immutable domain value object containing:

- a `BigDecimal` amount; and
- an ISO 4217 currency.

Version 1 permits GBP only.

Amounts must:

- have scale two;
- be validated during domain construction;
- reject implicit rounding;
- use `RoundingMode.UNNECESSARY` when enforcing scale; and
- never pass through `double` or `float`.

PostgreSQL stores supported monetary amounts as:

```sql
NUMERIC(19,2)
```

Double-entry ledger
-------------------

A ledger transaction contains two or more immutable ledger entries.

Each entry contains:

-   a ledger account identifier;
-   a side of `DEBIT` or `CREDIT`;
-   a positive amount;
-   a currency;
-   a sequence within the transaction; and
-   immutable descriptive metadata.

For each ledger transaction:

```
sum(DEBIT amounts) = sum(CREDIT amounts)
```

Version 1 does not support a multi-currency ledger transaction.

Invariant enforcement
---------------------

Enforce ledger balance through:

1.  a domain factory that cannot create an unbalanced journal;
2.  service-level transaction orchestration;
3.  a PostgreSQL deferred constraint trigger evaluated before commit;
4.  example-based tests;
5.  database integration tests; and
6.  property-based tests.

Balance snapshots
-----------------

Account balance snapshots are updated in the same database transaction as the\
ledger entries.

The immutable ledger remains authoritative.

A verification query can derive an account balance from ledger entries and\
compare it with the stored snapshot.

Concurrency
-----------

Account balance records use optimistic versioning.

An optimistic-lock conflict rolls back the entire payment transaction.

A bounded retry must:

1.  reload current account state;
2.  reload the current balance;
3.  reapply account-status rules;
4.  reapply available-balance rules; and
5.  attempt the transaction again only when still valid.

Version 1 has no overdraft facility.

A committed customer account balance must not be negative.

Corrections
-----------

Posted ledger entries cannot be edited or deleted.

Corrections use new compensating ledger transactions linked to the transaction\
being corrected.

Consequences
------------

### Positive

-   Monetary amounts remain exact.
-   Every movement has an auditable explanation.
-   Corruption is rejected at multiple layers.
-   Concurrent requests cannot silently overwrite balances.
-   Historical financial records remain reproducible.

### Negative

-   Posting is more complex than updating one balance column.
-   Deferred checks require PostgreSQL-specific migration code.
-   Snapshot and ledger consistency must be monitored.
-   Retry policy must distinguish concurrency conflicts from permanent failures.

Rejected alternatives
---------------------

### Binary floating point

Rejected because decimal financial amounts cannot be represented exactly.

### Balance-only model

Rejected because it does not provide double-entry auditability.

### Deriving every balance during every request

Rejected for routine reads because transaction histories can grow large.

Ledger-derived calculation remains available for verification.

### Mutable corrections

Rejected because they destroy the original financial history.
