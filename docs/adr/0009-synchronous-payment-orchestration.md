# ADR 0009: Synchronous payment orchestration and recovery

- Status: Accepted
- Date: 2026-06-29
- Decision owners: Project maintainer
- Clarifies: ADR 0003 concurrency and ADR 0006 idempotency
- Builds on: ADR 0008 ledger and GBP minor-unit model

## Context

Phase 5 introduces synchronous internal GBP payments.

A correct implementation must coordinate:

- an authenticated customer request;
- idempotency reservation and replay;
- an explicit payment lifecycle;
- source-account ownership;
- account status and available-balance rules;
- two account balance snapshots;
- one balanced immutable ledger transaction; and
- concurrent requests that may update the same accounts.

The payment module may depend on the account and ledger modules, but it must
not access either module's internal package.

Phase 7 will add the transactional outbox and Phase 10 will add the broader
business-audit capability. Phase 5 must leave a clear atomic extension point
without committing placeholder records for unimplemented phases.

## Decision

### Phase 5 scope

Phase 5 implements internal account-to-account payments in GBP.

It does not process real money, connect to an external bank, publish broker
events or claim production payment-processing suitability.

The synchronous API will provide:

```text
POST /api/v1/payments
GET  /api/v1/payments/{paymentId}
```

Payment submission requires an authenticated user with the `CUSTOMER` role.

The payment boundary obtains the authenticated identity UUID through the
public `CurrentIdentityUser.requireUserId()` identity-module API. The caller
does not provide an actor or customer identifier.

The payment module will add `identity` to its declared Spring Modulith
dependencies and will use only the identity module's public root-package API.

A customer may debit only an account owned by that customer's identity
assignment. The destination may be another active GBP customer account.

Customer users may read payments they submitted. The repository's concrete
privileged role names are `OPERATIONS` and `ADMIN`; either role may read any
payment for investigation. `RECONCILIATION_ANALYST` receives no Phase 5
payment-read permission.

### Request model

A payment request contains:

- source account identifier;
- destination account identifier; and
- positive GBP amount in integer minor units.

The source and destination identifiers must be different.

The operation name used for idempotency scope is:

```text
CREATE_INTERNAL_PAYMENT
```

### Idempotency key and fingerprint

`Idempotency-Key` is treated as an opaque, case-sensitive value after header
parsing.

It must contain between 1 and 128 visible ASCII characters and must not contain
control characters or whitespace.

The idempotency identity is:

```text
authenticated identity UUID
+ CREATE_INTERNAL_PAYMENT
+ Idempotency-Key
```

A unique PostgreSQL constraint protects that identity.

The server computes a versioned SHA-256 request fingerprint from canonical
request fields:

```text
fingerprint version
source account UUID
destination account UUID
GBP amount in minor units
```

Transport metadata and client-supplied hashes are excluded.

### Persisted idempotency response and retention

The idempotency record has two states:

```text
PROCESSING
COMPLETED
```

`COMPLETED` is the terminal state of the idempotency record. It is separate
from the payment lifecycle and means that a replayable HTTP response has been
stored, whether the payment itself completed, was rejected or failed.

A terminal idempotency record stores:

- the HTTP status code;
- the response media type;
- the exact UTF-8 JSON response body; and
- a retention expiry timestamp.

The allowed stored media types are:

```text
application/json
application/problem+json
```

Stored response bodies are limited to 16,384 UTF-8 bytes. The payment boundary
constructs deterministic response bodies from typed internal response models
before storage. Framework-specific `ProblemDetail` objects are not persisted.

Replayable problem responses contain stable `type`, `title`, `status`, `detail`
and `code` fields. Request-specific `instance`, correlation and tracing
metadata are not stored.

Terminal responses are retained for 24 hours from completion, evaluated through
the injected application clock. Cleanup may remove a terminal record only after
its retention expiry. Until cleanup removes it, the stored response remains
replayable. Reusing the same scoped key after cleanup creates a new logical
request.

A `PROCESSING` record contains an owner token and lease expiry but no stored
response or retention expiry. A `COMPLETED` record contains the stored response
and retention expiry but no active owner token or lease.

### Reservation transaction

A new idempotency key is reserved in a short transaction before financial
posting begins.

That reservation transaction creates:

- one payment in `PENDING`;
- one idempotency record in `PROCESSING`;
- the canonical fingerprint;
- the reserved payment identifier;
- a random processing-owner token;
- creation and update timestamps; and
- a bounded processing-lease expiry.

The default lease is five minutes and is evaluated through the injected
application clock so recovery tests remain deterministic.

For an existing idempotency identity:

- a different fingerprint returns `409 IDEMPOTENCY_KEY_REUSED`;
- an unexpired `PROCESSING` record returns
  `409 IDEMPOTENCY_REQUEST_IN_PROGRESS`;
- a completed terminal record returns its stored HTTP status and response
  without creating another payment; and
- an expired `PROCESSING` record with the same fingerprint may be reclaimed
  with a new owner token and lease.

A different fingerprint can never reclaim an expired record.

### Payment state machine

The accepted ADR 0006 state machine remains unchanged:

```text
PENDING -> PROCESSING
PROCESSING -> COMPLETED
PROCESSING -> REJECTED
PROCESSING -> FAILED
```

`COMPLETED`, `REJECTED` and `FAILED` are terminal.

A completed payment references exactly one ledger transaction.

A rejected or failed payment references no ledger transaction.

### Account-module boundary

The account module will expose a public payment-mutation API from its module
root package.

That API will:

- accept the authenticated identity identifier;
- resolve the identity-to-customer assignment through a non-throwing public
  customer-ownership lookup;
- load both source and destination accounts;
- enforce source-account ownership;
- enforce different account identifiers;
- require both accounts to be active;
- require GBP for both accounts;
- check source available balance before mutation;
- debit the source snapshot;
- credit the destination snapshot; and
- return immutable resulting account projections.

The customer module will add a non-throwing ownership lookup alongside the
existing `CustomerOwnership.requireCustomerId(UUID)` API. Expected absence of
an identity assignment must not escape through a transactional proxy as an
unchecked exception.

The payment-mutation API returns a typed result:

- `APPROVED` contains immutable source and destination account projections
  after mutation; or
- `REJECTED` contains a stable account-payment rejection reason and no mutated
  projections.

Expected business outcomes, including missing ownership, missing accounts,
inactive accounts, currency mismatch and insufficient funds, are represented
as `REJECTED` results. They are not thrown across a transactional service
boundary and therefore do not mark the payment transaction rollback-only.

Only unexpected technical failures and retryable concurrency failures throw.
All validation occurs before either balance is changed.

The payment module will not import account entities, repositories, statuses or
other classes from `account.internal`.

### Core posting transaction

The processing owner executes one PostgreSQL transaction that:

1. reloads and verifies the idempotency reservation and owner token;
2. loads the reserved payment;
3. transitions `PENDING` to `PROCESSING`;
4. invokes the account module's public payment-mutation API;
5. posts one ledger transaction of type `INTERNAL_PAYMENT`;
6. records the payment UUID as the ledger business reference;
7. creates a source `DEBIT` and destination `CREDIT` for the same amount;
8. stores the resulting ledger transaction identifier on the payment;
9. transitions the payment to `COMPLETED`; and
10. stores the `201 Created` response in the idempotency record.

The payment, account snapshots, ledger header, ledger entries and idempotent
success response commit together.

Any failure before commit rolls back all changes made by the core posting
transaction.

### Deterministic rejection

A deterministic business refusal is recorded in one transaction.

The transaction:

1. verifies the active idempotency owner;
2. transitions the payment from `PENDING` to `PROCESSING`;
3. receives a typed `REJECTED` account-payment result after all current
   account and payment rules are evaluated;
4. makes no balance or ledger changes;
5. transitions the payment to `REJECTED`; and
6. stores the stable problem response in the idempotency record.

Phase 5 uses `422 Unprocessable Content` for deterministic payment refusals,
with stable codes such as:

```text
PAYMENT_SOURCE_NOT_OWNED
PAYMENT_SOURCE_NOT_ACTIVE
PAYMENT_DESTINATION_NOT_ACTIVE
PAYMENT_INSUFFICIENT_FUNDS
```

Malformed input, authentication failures and missing idempotency headers are
rejected before reservation.

### Unexpected failure

An unexpected processing failure rolls back the core posting transaction.

A separate failure-finalisation transaction then:

1. verifies the processing owner;
2. transitions the still-`PENDING` payment through `PROCESSING` to `FAILED`;
3. confirms that no ledger transaction is attached; and
4. stores a bounded, non-sensitive problem response.

The stable error code is:

```text
PAYMENT_PROCESSING_FAILED
```

The original exception details are not stored in the client response.

If the process terminates before failure finalisation, the lease-based recovery
path allows the same request and fingerprint to reclaim the reservation.

### Optimistic concurrency and retry

Account snapshots retain JPA optimistic versions as required by ADR 0003.

An optimistic-lock, deadlock or serialization conflict rolls back the whole
core posting transaction.

The orchestration layer performs at most three total posting attempts for the
same reserved payment and owner token.

Every retry starts a new database transaction and must:

1. reload both accounts;
2. reload their latest balances and versions;
3. reapply ownership, status, currency and available-balance rules;
4. recreate the balanced ledger posting only inside that new transaction; and
5. commit only if every invariant still holds.

Rolled-back attempts leave no payment transition, snapshot mutation or ledger
record.

If concurrency remains unresolved after the final attempt, the payment is
finalised as `FAILED` with:

```text
PAYMENT_CONCURRENT_MODIFICATION
```

### Database constraints

The Phase 5 migration will protect at least:

- positive GBP payment amounts;
- different source and destination accounts;
- valid payment and idempotency states;
- one unique idempotency scope and key;
- a 64-character SHA-256 fingerprint;
- one unique ledger transaction per completed payment;
- a ledger reference if and only if payment status is `COMPLETED`;
- no ledger reference for `REJECTED` or `FAILED`; and
- bounded stored response and idempotency-key sizes.

Financial tables remain forward-only Flyway-managed schema.

### Later atomic extensions

Phase 7 will add an outbox record to the successful core posting transaction.

Phase 10 will add the applicable immutable business-audit record to the same
transaction.

Those phases must extend the existing transaction boundary rather than publish
or audit after commit.

Phase 5 does not create placeholder outbox or audit rows.

## Consequences

### Positive

- Duplicate requests cannot create duplicate payments.
- In-progress requests and stale reservations have explicit behaviour.
- A payment identifier exists before financial processing begins.
- Completed payment, snapshot and ledger state commit atomically.
- Business refusals are durable and replayable.
- Whole-transaction retries re-evaluate current financial rules.
- Account internals remain encapsulated behind a public module API.
- Later outbox and audit work has a documented atomic extension point.

### Negative

- Reservation and financial posting use separate transactions.
- A lease and recovery path are required.
- Stored HTTP responses require bounded retention.
- Whole-transaction retry logic is more complex than a single annotated
  method.
- Unexpected failure finalisation requires careful ownership checks.

## Rejected alternatives

### Controller-only duplicate suppression

Rejected because it does not protect concurrent requests or multiple
application instances.

### Reserving idempotency only inside the posting transaction

Rejected because another request cannot reliably distinguish active processing
and a crash leaves no durable recovery identity.

### Accessing account internals from payment orchestration

Rejected because it violates the executable Spring Modulith boundary.

### Updating balance snapshots outside the ledger transaction

Rejected because a committed snapshot could disagree with the authoritative
ledger.

### Unbounded automatic retry

Rejected because persistent contention must remain visible and request latency
must stay bounded.

### Pessimistic locking as the default concurrency model

Rejected for version 1 because ADR 0003 already selected optimistic versioning
and full rule re-evaluation. Database deadlocks and serialization conflicts
remain bounded retry candidates.

### Placeholder outbox or audit records

Rejected because Phase 5 must not claim unimplemented asynchronous or audit
behaviour.

## Revisit triggers

Revisit this decision when:

- load testing shows three attempts are insufficient or excessive;
- measured processing time requires a different lease duration;
- multiple application instances require distributed recovery workers;
- another payment type changes fingerprint fields;
- another currency is introduced; or
- Phase 7 or Phase 10 extends the posting transaction.
