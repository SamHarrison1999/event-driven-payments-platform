# Product scope

## Purpose

Build a realistic educational simulation of an internal payment platform with
double-entry ledgering, asynchronous event delivery and settlement
reconciliation.

The project is intended to demonstrate software architecture and engineering
skills.

It is not a real financial product and must not be used to process real money.

## Users

### Customer user

A customer user can:

- register and authenticate;
- view accounts assigned to them;
- view account balances;
- view transaction history;
- submit internal payments; and
- track payment status.

### Operations user

An operations user can:

- create simulated customers;
- create and manage customer accounts;
- investigate failed or rejected payments; and
- view relevant operational audit information.

### Reconciliation analyst

A reconciliation analyst can:

- upload synthetic settlement files;
- review settlement-import results;
- investigate unmatched or mismatched records; and
- record discrepancy-resolution decisions.

### Administrator

An administrator can:

- manage user roles;
- inspect operational diagnostics;
- inspect dead-letter events;
- replay eligible failed events;
- access demonstration failure controls; and
- review security-sensitive audit events.

## Version 1 scope

Version 1 includes:

- local user registration;
- secure authentication;
- role-based access control;
- PostgreSQL-backed browser sessions;
- simulated customer profiles;
- GBP customer accounts;
- internal account-to-account payments;
- immutable double-entry ledger entries;
- account balances and transaction history;
- idempotent payment submission;
- explicit payment state transitions;
- optimistic concurrency control;
- transactional outbox records;
- Kafka-compatible asynchronous events;
- simulated notifications;
- CSV settlement-file import;
- deterministic reconciliation;
- discrepancy review and resolution;
- immutable audit events;
- operational reporting;
- structured logs;
- correlation identifiers;
- distributed tracing;
- Prometheus metrics;
- health and readiness endpoints;
- rate limiting;
- a demonstration failure simulator;
- payment-path load testing;
- Docker Compose deployment;
- security documentation;
- portfolio screenshots; and
- a five-minute interview demonstration.

## Out of scope for version 1

Version 1 does not include:

- processing real money;
- external bank connectivity;
- card processing;
- direct debit schemes;
- foreign exchange;
- multi-currency transfers;
- multiple legal entities;
- interest calculation;
- chargebacks;
- regulated fraud detection;
- production identity federation;
- regulatory certification;
- PCI DSS certification;
- PSD2 certification;
- FCA authorisation;
- multi-region deployment;
- high-availability database clustering;
- production disaster recovery;
- Kubernetes before the Docker release is stable; or
- claims that the platform is suitable for banking production use.

## Financial rules

1. Version 1 supports GBP only.
2. GBP amounts have two fractional decimal places.
3. Accounts do not have an overdraft facility.
4. A payment must have different source and destination accounts.
5. Both accounts must be active.
6. Both accounts must use the same currency.
7. A debit must not exceed the source account's available balance.
8. A completed payment has exactly one balanced ledger transaction.
9. Posted ledger entries are immutable.
10. Corrections use compensating ledger transactions.
11. Reconciliation status is separate from payment-processing status.
12. Failed event delivery does not reverse a valid ledger posting.

## System invariants

- Every ledger transaction has total debits equal to total credits.
- Every ledger transaction contains at least two entries.
- Ledger entry amounts are positive.
- No committed customer account balance is negative.
- One idempotency scope and key identifies no more than one logical request.
- Reusing an idempotency key for a different request is rejected.
- Payment states change only through the documented state machine.
- Payment posting and outbox creation commit atomically.
- Event consumers tolerate duplicate delivery.
- Normal application users cannot update or delete audit records.
- Normal application users cannot update or delete posted ledger entries.

## Initial assumptions

- PostgreSQL is the system of record.
- The application initially runs as one backend process.
- Account balance snapshots are updated in the same transaction as ledger
  postings.
- Ledger entries remain the authoritative financial record.
- Event delivery is at least once.
- Event consumers are idempotent.
- Settlement files contain synthetic data only.
- Version 1 is developed and demonstrated in a local Docker environment.

## Success criteria

Version 1 is successful when a reviewer can:

1. start the system from a clean checkout;
2. register and authenticate;
3. create or access a simulated account;
4. execute an internal payment;
5. inspect the payment's balanced ledger entries;
6. repeat the request without creating a duplicate;
7. demonstrate concurrent-payment protection;
8. interrupt broker delivery without losing the committed outbox event;
9. restore delivery and observe safe processing;
10. upload a settlement file;
11. inspect reconciliation discrepancies;
12. correlate a request across logs and traces;
13. reproduce the documented load test; and
14. follow the five-minute demonstration without undocumented setup.
