# ADR 0011: Implement asynchronous events through a persisted outbox

- Status: Accepted
- Date: 2026-07-06
- Decision owners: Project maintainer

## Context

Phase 5 made internal payment processing synchronous and atomic. A completed
payment now updates account balances, posts a balanced ledger transaction,
transitions the payment to `COMPLETED` and stores the exact idempotent response
inside one PostgreSQL transaction.

The architecture deliberately deferred outbox and business-audit writes until a
later phase so they could extend the payment transaction instead of writing
after commit.

Phase 7 introduces that transactional outbox boundary.

The project still runs as a modular monolith. A real Kafka-compatible broker is
planned, but Phase 7 must remain demonstrable in local development without
adding broker infrastructure or weakening existing payment guarantees.

## Decision

Phase 7 will add a project-owned outbox module and database schema.

Payment completion will write a `payment.completed.v1` outbox event in the
same PostgreSQL transaction that completes the payment, records the ledger
transaction and stores the terminal idempotent response.

Rejected and failed payments will remain terminal payment outcomes, but Phase 7
will not publish them as downstream business events. That keeps the first
asynchronous event boundary narrow and focused on successful financial posting.

Outbox records will include:

- event identifier;
- aggregate type;
- aggregate identifier;
- event type;
- schema version;
- JSON payload;
- correlation identifier;
- causation identifier;
- creation time;
- delivery status;
- attempt count;
- next-attempt time;
- last error category;
- last error message; and
- publication time when successful.

The first supported event type is:

```text
payment.completed.v1
```

The event payload will include non-sensitive identifiers and exact integer
minor-unit values:

```json
{
  "paymentId": "uuid",
  "ledgerTransactionId": "uuid",
  "actorIdentityId": "uuid",
  "sourceAccountId": "uuid",
  "destinationAccountId": "uuid",
  "amountMinorUnits": 12345,
  "currency": "GBP",
  "completedAt": "2026-07-06T12:00:00Z"
}
```

The outbox publisher will claim bounded batches from PostgreSQL using row locks
and `SKIP LOCKED`. Claimed events move to `PUBLISHING` with an owner token and
lease expiry.

A successful simulated publication moves an event to `PUBLISHED`.

Retryable failures move an event back to `PENDING` with an incremented attempt
count and a future `next_attempt_at`.

Exhausted events move to `DEAD_LETTER`.

Phase 7 will not add administrator replay APIs, notification delivery or
consumer-side idempotency tables. Those belong to later phases.

## Consequences

### Positive

- A completed payment cannot lose its intended downstream event.
- Broker outages do not roll back valid payments.
- Delivery state is visible and testable in PostgreSQL.
- The portfolio can demonstrate at-least-once publication without requiring
  external infrastructure.
- Later notification, reconciliation and reporting consumers can subscribe to a
  stable event contract.

### Negative

- Publication is eventually consistent rather than immediate.
- Duplicate publication remains possible after lease recovery.
- Outbox retention and dead-letter operations remain future work.
- The database must store payload and delivery diagnostics.
- A real broker adapter still needs to be added later.

## Rejected alternatives

### Publish directly from payment processing

Rejected because it creates a database-and-broker dual write.

### Use Spring application events only

Rejected because in-process events are not durable after process failure.

### Add Kafka infrastructure immediately

Rejected because the phase goal is to establish the persisted transactional
boundary first. Broker infrastructure can be added after the event contract,
claiming model and retry behaviour are stable.
