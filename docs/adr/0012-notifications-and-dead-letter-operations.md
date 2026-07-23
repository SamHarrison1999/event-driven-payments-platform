# ADR 0012: Add durable simulated notifications and controlled dead-letter operations

- Status: Accepted
- Date: 2026-07-23
- Decision owners: Project maintainer

## Context

Phase 7 established a project-owned transactional outbox with at-least-once
publication, bounded claiming, retry scheduling and dead-letter classification.

The next phase must demonstrate a durable consumer side effect without adding
real email, SMS or broker infrastructure. It must also make dead-letter events
inspectable and replayable without allowing administrators to mutate financial
history or event payloads.

Duplicate delivery remains possible after an outbox publication lease expires.
A notification consumer therefore cannot rely on exactly-once transport.

## Decision

### Published-event consumption

The outbox module will expose a narrow read-only public API for bounded pages of
published event snapshots ordered by publication time and event identifier.

The notification module will own a durable consumer checkpoint and a unique
source-event identifier. Persisting a notification or a terminal consumer
failure and advancing the checkpoint will occur in one PostgreSQL transaction.

The first consumer accepts only `payment.completed.v1` schema version 1.
Unknown event types remain available for later consumers. An invalid supported
payload is recorded as a consumer failure instead of silently skipped.

The unique source-event identifier is the primary duplicate-side-effect guard.
Re-reading the same published event must not create a second notification.

### Notification model

A simulated payment notification records:

- notification identifier;
- source outbox event identifier;
- recipient identity-user identifier;
- payment identifier;
- completion time;
- exact GBP minor units;
- delivery status;
- attempt count;
- next-attempt time;
- owner-token delivery lease;
- bounded failure diagnostics;
- creation, update and delivery times; and
- optimistic-lock version.

Notification content must not contain credentials, session data or unrestricted
account details.

The delivery lifecycle is:

- `PENDING`;
- `DELIVERING`;
- `DELIVERED`; or
- `DEAD_LETTER`.

Delivery workers use bounded PostgreSQL `FOR UPDATE SKIP LOCKED` claims, owner
tokens and expiring leases. Retryable failures use bounded exponential backoff
with deterministic jitter. Permanent or exhausted failures enter notification
dead letter.

The Phase 8 sink remains simulated. It records a deterministic delivery outcome
and may log only non-sensitive identifiers.

### Customer access

An authenticated customer may list only notifications addressed to their own
identity-user identifier. The API is read-only in Phase 8 and returns exact,
structured payment-completion information.

The customer workspace may display delivered simulated notifications after the
backend contract is complete. It must preserve the existing session-expiry and
customer-cache-isolation rules.

### Outbox dead-letter administration

Only an authenticated `ADMIN` may:

- list dead-letter outbox events;
- inspect bounded event and failure metadata; and
- request controlled replay.

Replay is allowed only from `DEAD_LETTER`. It does not modify the event
identifier, aggregate identity, event type, schema version, payload,
correlation identifier, causation identifier or creation time.

A successful replay request:

- returns the event to `PENDING`;
- clears publication-owner and failure state;
- schedules immediate publication;
- increments replay metadata; and
- writes an immutable replay-audit record containing the administrator identity,
  reason and timestamp.

Optimistic locking prevents two administrators from replaying the same event
concurrently. Notification consumer deduplication protects against duplicate
side effects when the same source event is published again.

### Security and response behaviour

Administrator endpoints use the existing session, CSRF and role enforcement.
Customer notification queries use `CurrentIdentityUser` and do not accept a
caller-supplied recipient identifier.

Malformed identifiers, validation failures, missing resources, forbidden
access and state conflicts use the existing `application/problem+json`
conventions.

## Consequences

### Positive

- The project demonstrates durable at-least-once consumption and deduplication.
- Notification delivery failures are independent of payment correctness.
- Administrators can investigate and replay eligible outbox failures.
- Replay actions are attributable without mutating the original event.
- The design remains testable without Kafka, email or SMS infrastructure.

### Negative

- Notification delivery introduces another leased retry lifecycle.
- A consumer checkpoint and deduplication record add persistence complexity.
- Controlled replay can intentionally cause another delivery attempt.
- The public outbox read API must remain narrow to preserve module ownership.

## Out of scope for Phase 8

- real email, SMS or push providers;
- Kafka-compatible broker deployment;
- arbitrary event subscription management;
- payload editing during replay;
- deleting dead-letter events;
- bulk replay;
- notification preferences; and
- settlement, reconciliation, reporting or observability work.
