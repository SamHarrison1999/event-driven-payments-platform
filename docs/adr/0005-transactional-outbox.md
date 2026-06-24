# ADR 0005: Use an explicit transactional outbox

- Status: Accepted
- Date: 2026-06-24
- Decision owners: Project maintainer

## Context

Publishing directly to a broker during payment processing creates a dual-write
problem.

The database may commit while broker publication fails.

The broker publication may succeed while the database transaction rolls back.

A distributed transaction is disproportionate for this project and would
reduce broker portability.

## Decision

Write an explicit outbox record in the same PostgreSQL transaction as the
business change.

The outbox record will contain:

- event identifier;
- aggregate type;
- aggregate identifier;
- event type;
- schema version;
- serialised payload;
- correlation identifier;
- causation identifier;
- creation time;
- delivery status;
- attempt count;
- next-attempt time;
- last error category; and
- publication time when successful.

A publisher will claim bounded batches using PostgreSQL locking such as:

```sql
FOR UPDATE SKIP LOCKED
```

Delivery semantics are at least once.

Consumers must persist a processed-event identifier or use another\
domain-specific idempotency mechanism.

Retries use bounded exponential backoff with jitter.

Exhausted or permanently invalid events enter a dead-letter state.

Dead-letter events support:

-   administrator inspection;
-   an audit trail;
-   controlled replay; and
-   protection against duplicate side effects.

Spring Modulith usage
---------------------

Spring Modulith is initially used for:

-   module modelling;
-   architecture verification;
-   module-focused testing; and
-   architecture documentation.

Durable external publication remains an explicit project-owned outbox so its\
schema, transaction boundaries and failure behaviour remain visible and\
explainable.

Ordering
--------

The platform guarantees ordering only where:

-   events share a documented aggregate key;
-   broker partitioning preserves that key; and
-   the consumer respects partition ordering.

The platform does not claim a total global event order.

Consequences
------------

### Positive

-   A committed business change cannot lose its intended event.
-   Broker outages do not roll back valid payments.
-   Delivery state remains inspectable.
-   Failure scenarios can be demonstrated deterministically.
-   At-least-once behaviour can be explained in interviews.

### Negative

-   Publication is not instantaneous.
-   Duplicate delivery remains possible.
-   Outbox retention and cleanup are required.
-   Consumers require idempotency.
-   Ordering rules must be explicit.

Rejected alternatives
---------------------

### Direct broker publication

Rejected because it creates an unsafe database and broker dual write.

### Distributed transaction

Rejected because of operational complexity and limited portability.

### In-memory event queue

Rejected because process failure could lose committed events.
