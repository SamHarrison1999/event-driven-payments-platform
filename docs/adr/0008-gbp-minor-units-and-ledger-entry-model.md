# ADR 0008: Use GBP minor units and explicit-sided ledger entries

- Status: Accepted
- Date: 2026-06-29
- Decision owners: Project maintainer
- Supersedes: ADR 0003 money representation and PostgreSQL monetary storage

## Context

ADR 0003 selected exact decimal money, balanced double-entry transactions,
immutable postings, auditable corrections and transactional balance snapshots.

Phase 3 subsequently established the implemented GBP value model:

- `GbpAmount` stores a non-negative signed 64-bit count of minor units;
- GBP has a fixed scale of two;
- conversion from major units rejects implicit rounding;
- arithmetic uses exact overflow-checked integer operations; and
- PostgreSQL stores account balances as `BIGINT` minor units.

Keeping ledger values in the same representation avoids conversion drift
between account snapshots and authoritative ledger postings. The ledger model
also needs to distinguish debit and credit without encoding one side as a
negative monetary amount.

## Decision

### Money representation

Version 1 supports GBP only.

Application monetary values use `GbpAmount`, represented as a non-negative
signed 64-bit count of pennies.

The application:

- never uses `float` or `double` for monetary values;
- rejects negative amounts;
- rejects fractional pennies;
- rejects arithmetic overflow;
- preserves a scale of two when converting to or from major units; and
- performs no implicit rounding.

PostgreSQL stores GBP amounts as `BIGINT` minor units.

### Ledger transaction

A ledger transaction is an immutable journal containing:

- a unique transaction identifier;
- a stable transaction type;
- an optional external or business reference;
- the posting timestamp;
- immutable descriptive metadata; and
- two or more ordered ledger entries.

Each ledger entry contains:

- a unique entry identifier;
- its parent transaction identifier;
- a ledger account identifier;
- an explicit side of `DEBIT` or `CREDIT`;
- a positive `GbpAmount`;
- the GBP currency code;
- a one-based sequence within the transaction; and
- immutable descriptive metadata.

Negative entry amounts are not permitted. Debit and credit meaning is carried
only by the explicit side.

### Balancing rules

Every ledger transaction must:

- contain at least two entries;
- contain at least one debit and one credit;
- use GBP for every entry;
- use each sequence number no more than once; and
- satisfy:

```text
sum(DEBIT minor units) = sum(CREDIT minor units)
```

A domain factory rejects an invalid or unbalanced journal before persistence.

A PostgreSQL deferred constraint trigger independently verifies the same
balance invariant before commit.

### Persistence and immutability

Ledger transaction headers and entries are append-only financial records.

Normal application operations cannot update or delete a posted ledger
transaction or entry.

Corrections create a new compensating ledger transaction linked to the
original transaction. The original record remains unchanged.

### Account balance snapshots

The immutable ledger is the authoritative financial record.

A later payment-posting transaction will atomically:

1. validate account status and available balance;
2. create one balanced ledger transaction;
3. create all ledger entries;
4. update affected account balance snapshots; and
5. commit all changes together.

A verification query will derive balances from ledger entries and compare
those results with account snapshots.

### Module boundary

The ledger module owns ledger transactions, ledger entries and ledger
verification queries.

The ledger module depends only on `shared`.

Payment orchestration may call public APIs exposed by both the account and
ledger modules. The ledger module does not access account-module internals.

## Consequences

### Positive

- Ledger postings and account snapshots use one exact monetary representation.
- Debit and credit semantics remain explicit.
- Negative monetary values cannot hide posting direction.
- Domain and database checks independently protect balancing.
- Ledger history remains reproducible and auditable.
- Later payment orchestration can commit ledger and snapshot changes
  atomically.

### Negative

- The supported range is bounded by signed 64-bit minor units.
- PostgreSQL-specific deferred trigger logic is required.
- Compensating transactions increase journal volume.
- Balance verification queries must interpret debit and credit sides
  correctly.

## Rejected alternatives

### `NUMERIC(19,2)` with `BigDecimal`

Rejected for version 1 because the implemented account model already uses
overflow-checked integer minor units. Mixing representations would add
conversion paths without improving GBP precision.

### Signed ledger amounts

Rejected because a negative amount combines magnitude and posting direction,
making validation and auditing less explicit.

### Balance-only records

Rejected because they cannot explain or reproduce value movements.

### Mutable corrections

Rejected because changing or deleting a posted entry destroys the original
financial history.

## Revisit triggers

Revisit this decision when:

- another currency is introduced;
- supported values could exceed signed 64-bit minor units;
- fractional-minor-unit instruments are required;
- an external accounting integration requires a different representation; or
- measured storage or query behaviour justifies a compatible alternative.