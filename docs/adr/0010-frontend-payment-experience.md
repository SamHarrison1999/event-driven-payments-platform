# ADR 0010: Frontend payment experience

- Status: Accepted
- Date: 2026-07-06
- Decision owners: Project maintainer
- Builds on: ADR 0006 idempotency and ADR 0009 synchronous payment orchestration
- Applies to: Phase 6 frontend implementation

## Context

Phase 5 established authenticated synchronous payments, durable idempotency,
customer-owned account queries and customer-owned payment lookup. The React
client is still the Phase 1 platform shell and does not exercise those
capabilities.

Phase 6 must introduce the first complete browser customer journey without
weakening the backend's session, CSRF, ownership, money or idempotency
guarantees.

The frontend must coordinate:

- a PostgreSQL-backed server-side browser session;
- CSRF protection for state-changing requests;
- customer-owned accounts;
- GBP values represented as integer minor units;
- one idempotency key for one logical payment request;
- JSON and RFC 9457 problem contracts;
- uncertain network outcomes and session expiry; and
- accessible interaction states.

The educational and no-real-money boundary remains visible.

## Decision

### Scope

Phase 6 allows a customer to:

1. sign in and restore an existing session;
2. view owned GBP accounts and balances;
3. prepare and confirm an internal payment;
4. submit it with retry-safe idempotency;
5. understand completed, rejected, failed and in-progress outcomes;
6. retrieve an owned payment by identifier; and
7. sign out and clear customer-specific client state.

Phase 6 does not add administration, payment-history pagination, asynchronous
events, notifications, settlement, reconciliation, external bank connections
or real-money capability.

A recent-payment feed will not be simulated from browser memory. It requires a
future bounded backend collection API.

### Application composition

The client uses feature folders for identity, accounts, payments and system
status, plus shared API, validation, identifier and money utilities.

TanStack Query remains the server-state mechanism.

Phase 6 uses one accessible workspace with navigation between accounts, payment
creation and payment lookup. A routing dependency is deferred until durable
deep links, nested screens or browser-history semantics are required.

### Shared API boundary

API URLs use `VITE_API_BASE_URL` when configured and otherwise the current
browser origin.

Authenticated requests use:

```text
credentials: include
```

The shared client will:

- set explicit `Accept` values;
- set JSON `Content-Type` only when a body is present;
- support `AbortSignal`;
- parse success JSON and problem JSON separately;
- reject unexpected media types;
- expose typed HTTP, network and contract errors; and
- never log passwords, cookies, CSRF tokens or payment bodies.

JavaScript does not read or persist the server-side session cookie.

### Runtime validation

TypeScript types do not validate network data. Every Phase 6 API boundary will
validate `unknown` JSON before returning typed values.

Explicit validators and type guards will follow the existing system-information
client. No schema-validation dependency is added in Phase 6.

Malformed responses become contract errors and are not rendered as trusted
customer or payment information.

### Session and cache isolation

Startup queries:

```text
GET /api/v1/identity/session
```

An unresolved query shows a neutral bootstrap state. A successful response
establishes the user id, email and roles. `401 Unauthorized` establishes the
signed-out state.

After login, the current-session query is refreshed and customer data loads only
for that identity.

After logout, identity change or session expiry:

- customer account and payment queries are removed;
- unresolved idempotency state is cleared;
- in-memory CSRF state is cleared; and
- the signed-out experience is shown.

### CSRF

The frontend obtains the CSRF header name and token from:

```text
GET /api/v1/identity/csrf
```

The token is held only in memory and attached to mutations, including login,
logout and payment submission when required by backend security.

It is never written to local storage, session storage, logs or error messages.
Authentication transitions invalidate the cached token.

### Exact GBP handling

The user enters decimal GBP text. The frontend does not parse payment money
with `Number`, `parseFloat` or floating-point multiplication.

Validated text converts directly to integer minor units:

```text
"10"    -> 1000
"10.5"  -> 1050
"10.50" -> 1050
```

The parser rejects zero, negative values, more than two fractional digits,
exponent notation, currency symbols, separators, non-decimal characters and
values outside the supported safe integer range.

Backend minor units are formatted with `Intl.NumberFormat` using GBP. The
backend remains authoritative for balance, account state and financial rules.

### Payment draft and confirmation

A draft contains exactly:

- source account UUID;
- destination account UUID; and
- amount in GBP minor units.

All owned accounts may be displayed, but only an active account can be selected
as a source. The destination is a UUID and need not be owned by the customer.

Before submission, a confirmation view displays the exact source, destination
and formatted amount.

Client validation improves usability but never replaces backend ownership,
status, currency, funds or concurrency checks.

### Idempotency lifecycle

One logical payment owns one opaque key generated with:

```text
crypto.randomUUID()
```

The key is generated only when a valid confirmed draft is first submitted and
is bound to the canonical source UUID, destination UUID and minor-unit amount.

The same key is reused after a network-uncertain result, a deliberate retry of
the unchanged draft, an in-progress response or a browser reload with a valid
unresolved envelope.

A new key is required after any canonical field changes, a terminal result, a
new payment, an identity change or envelope expiry. Double submission is
disabled while a mutation is active.

A rerender or fetch retry must never generate a replacement key.

### Bounded unresolved-request recovery

To survive reload after an uncertain response, the frontend may retain one
unresolved envelope in `sessionStorage` containing only:

- schema version;
- authenticated user UUID;
- idempotency key;
- source and destination UUIDs;
- amount in minor units; and
- creation timestamp.

It contains no credentials, cookies, session or CSRF tokens, complete responses
or arbitrary server errors.

The envelope is validated before use and cleared after a terminal response,
draft edit, new payment, logout, identity change, malformed storage or 24-hour
expiry. It is never copied to `localStorage`.

### Outcomes and lookup

`COMPLETED` displays the payment id, accounts, amount, currency, ledger
transaction id and timestamps, then refreshes owned accounts.

`REJECTED` displays the stable business reason and states that no payment was
posted.

`FAILED` displays a bounded technical outcome and payment id without inventing
internal detail.

An in-progress response keeps the current key and offers a safe retry or lookup
path. An idempotency-key reuse conflict is surfaced as a client-state error and
is not hidden by generating a new key.

Payment lookup uses:

```text
GET /api/v1/payments/{paymentId}
```

Missing and foreign payments remain indistinguishable to customer users.

### Accessibility

Every control has a visible label and accessible name. Validation messages are
associated with fields. Results use an appropriate live region, and focus moves
to the error summary or result heading after an interaction.

Status uses text and structure, not colour alone. All workflows support keyboard
operation.

Explicit states cover session bootstrap, signed out, login, account loading and
empty data, draft, confirmation, submission, terminal outcomes, lookup,
malformed contracts and expired authentication.

### Testing and verification

Phase 6 uses Vitest, Testing Library, `user-event` and MSW.

Unit tests cover API validators, problem parsing, UUID validation, GBP handling
and idempotency-envelope lifecycle.

Workflow tests cover session restoration, login, logout, cache isolation,
accounts, confirmation, successful submission, account refresh, rejection,
failure, same-key retry, reload recovery, double-submit prevention, session
expiry, lookup and accessible focus behaviour.

The cumulative Phase 6 verifier runs prior-phase verification, frontend lint,
all frontend tests, the production build, documentation checks and whitespace
checks.

## Consequences

### Positive

- The browser exercises the real authenticated payment boundary.
- Session, CSRF, ownership and idempotency remain aligned with the backend.
- Exact money handling avoids floating-point corruption.
- Uncertain requests can be retried safely within the browser session.
- Runtime validation prevents malformed network data becoming trusted UI state.
- The experience remains small enough for thorough verification.

### Negative

- Manual validators add code beside TypeScript interfaces.
- CSRF acquisition and invalidation add mutation complexity.
- Session-storage recovery requires schema, identity and expiry validation.
- One workspace does not provide deep links for individual views.

## Rejected alternatives

### Store authentication tokens in local storage

Rejected because the backend uses a server-side session and persistent
JavaScript-readable tokens would weaken that model.

### Convert GBP through floating-point numbers

Rejected because binary floating-point arithmetic is not an acceptable money
boundary.

### Generate a new idempotency key for every fetch attempt

Rejected because a network retry could create a second logical payment.

### Keep idempotency state only in component memory

Rejected because reload after an uncertain response would lose the safe retry
identity.

### Simulate payment history from browser state

Rejected because browser memory is not an authoritative, complete or
ownership-enforced collection.

### Add routing immediately

Rejected because the three Phase 6 customer views do not yet require deep links
or nested routes.

## Revisit triggers

Revisit when the backend adds payment collections, operational screens require
deep links, multiple unresolved submissions are supported, authentication moves
to tokens, currencies expand or measured usability shows the single workspace
is insufficient.
