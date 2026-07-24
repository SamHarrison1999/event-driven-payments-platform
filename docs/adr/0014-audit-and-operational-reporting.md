# ADR 0014: Add immutable audit search and bounded operational reporting

- Status: Accepted
- Date: 2026-07-24
- Decision owners: Project maintainer

## Context

Phases 2, 8 and 9 already create authoritative immutable evidence for
administrator role changes, outbox dead-letter replay and settlement
discrepancy resolution. Those records are deliberately owned by identity,
outbox and reconciliation. Phase 9 explicitly rejected coupling resolution to
the placeholder audit module before a general audit contract existed.

The platform also contains business transitions whose current tables describe
the latest durable state but are not a searchable cross-domain audit journal.
Examples include customer and account lifecycle changes, payment submission and
terminal payment outcomes, and accepted settlement imports.

Phase 10 must provide credible audit search and operational reporting without:

- mutating or replacing a domain source of truth;
- inventing historical events that were not recorded at the time;
- duplicating existing immutable evidence under a second owner;
- exposing credentials, sessions, idempotency keys, raw files or payloads;
- using unbounded queries or exports; or
- presenting reporting projections as financial ledger truth.

Reporting is an operational read capability. It cannot correct a payment,
account, ledger posting, outbox event, settlement row, reconciliation result or
resolution decision.

## Decision

### Evidence ownership

Identity continues to own `identity_security_event`. Outbox continues to own
`outbox_replay_audit`. Reconciliation continues to own
`settlement_resolution`.

Each source module exposes only the bounded public read model needed to
normalize its evidence. Reporting composes those models with canonical audit
events. It does not access another module's repository or JPA entity.

Existing rows are not copied into the canonical journal. A migration cannot
reliably recreate the original request correlation, recording time, actor kind
or event schema. Search results therefore expose an explicit source and the API
documents the coverage start of each canonical event family.

### Canonical business-audit journal

The audit module owns a new append-only `business_audit_event` table introduced
by Flyway migration V19.

Each event contains:

- a UUID event identifier;
- a versioned, allow-listed event type;
- an event schema version;
- the UTC time the business action occurred;
- the UTC time the event was recorded;
- actor kind `IDENTITY_USER` or `SYSTEM`;
- an identity-user identifier only for an identity actor;
- a bounded subject type and subject identifier;
- a bounded source-module name;
- a bounded source-record type and identifier;
- a bounded source-event identifier for this business occurrence;
- a bounded request or job correlation identifier; and
- bounded JSON metadata validated against the event type's allow-list.

The first canonical event families cover:

- customer creation and status change;
- account creation and status change;
- identity-to-customer assignment;
- payment submission;
- completed, rejected and failed payment outcomes; and
- accepted settlement imports.

Role changes, outbox replay and discrepancy resolution are represented from
their existing source-owned records and are not recorded again as canonical
events.

### Atomic and idempotent recording

The audit module exposes a narrow public recording service. A source module
records its canonical event in the same PostgreSQL transaction as the business
mutation or terminal state transition.

The source module supplies a stable source-event identifier for the specific
business occurrence. A repeated lifecycle change on the same source record
therefore has a different identifier, while a retry of the same occurrence
reuses it. This is an internal mutation, transition or import occurrence key,
not a client HTTP idempotency key.

A database unique constraint over source module, event type, source-record
type, source-record identifier and source-event identifier makes a retry
idempotent. Repeating the exact event returns the existing identifier. Reusing
the key with different immutable content is a conflict and rolls back the
surrounding transaction.

PostgreSQL rejects update and delete of canonical events. Application entities
also mark immutable columns as non-updatable. Audit recording never swallows a
failure: if required evidence cannot be persisted, the associated business
mutation does not commit.

### Metadata minimization

Metadata is not an arbitrary copy of a request or domain entity. Each event type
has a versioned schema of permitted keys, value types and lengths.

The following are forbidden:

- passwords or password hashes;
- session, cookie or CSRF values;
- idempotency keys or request fingerprints;
- raw settlement rows or files;
- outbox or notification payloads;
- arbitrary HTTP headers;
- exception stack traces; and
- free-text operator reasons.

Identifiers, stable reason codes, lifecycle states, GBP minor-unit amounts,
currency codes and bounded counts may be recorded when required by the event
schema.

### Normalized audit search

The reporting module owns the cross-domain normalized audit projection. Every
result has:

- globally stable source-qualified event identifier;
- source;
- category;
- event type and schema version;
- occurred-at time;
- actor kind and permitted actor identifier;
- subject type and identifier;
- correlation identifier; and
- safe typed details.

Search is ordered by `(occurredAt DESC, eventId DESC)`. The cursor is opaque,
validated and bound to the active filter set. Page size defaults to 50 and is
capped at 100.

Supported filters are:

- UTC `from` inclusive and `to` exclusive;
- category;
- exact event type;
- actor identifier;
- subject type and identifier;
- exact correlation identifier; and
- source.

Filters are conjunctive. Empty, malformed, unsupported and over-broad filters
return the established problem response rather than silently changing meaning.

### Authorization and field visibility

`ADMIN` can search all audit categories and use every report family.

`OPERATIONS` can search operational customer, account and payment evidence and
use payment operational reports. It cannot read identity security or
administrator recovery evidence.

`RECONCILIATION_ANALYST` can search settlement and reconciliation evidence and
use settlement and reconciliation reports. It cannot read customer-management,
identity security or administrator recovery evidence.

`CUSTOMER` has no Phase 10 audit, summary or export access.

The backend intersects requested filters with the caller's permitted
categories before querying. Authorization therefore happens before
pagination, aggregation and export. The frontend never determines the security
scope.

Actor and subject identifiers are returned only when permitted for the
caller's category. Email addresses, names and free-text evidence are not part of
the normalized search result.

### Operational summaries

`GET /api/v1/reports/operational-summary` accepts a required bounded UTC
half-open time window and returns separate typed sections.

The payment section reports:

- submitted and terminal payment counts;
- completed, rejected and failed counts;
- completed GBP minor-unit totals; and
- stable rejection and failure-code counts.

The settlement section reports:

- accepted import and row counts;
- matched and discrepancy counts; and
- import outcome totals.

The reconciliation section reports:

- discrepancy counts by stable code and lifecycle state;
- resolution counts by decision; and
- open discrepancy age bands.

Every value is computed from the relevant authoritative tables in one
read-only, repeatable-read PostgreSQL transaction so all sections observe the
same snapshot. Reports do not derive balances, replace ledger verification or
claim to be accounting statements.

### Bounded CSV exports

The reporting API provides:

- `GET /api/v1/reports/audit-events.csv`;
- `GET /api/v1/reports/payments.csv`;
- `GET /api/v1/reports/settlements.csv`; and
- `GET /api/v1/reports/reconciliation.csv`.

Every export:

- requires `from` and `to`;
- uses a UTC half-open window no longer than 31 days;
- contains at most 10,000 data rows;
- fails explicitly when the result would exceed the row cap;
- uses a fixed documented column order;
- uses UTF-8, RFC 4180 quoting and CRLF records;
- excludes free-text evidence and arbitrary metadata;
- contains only typed identifiers, ISO-8601 instants, enums, booleans and
  decimal integer values;
- returns a fixed safe filename; and
- uses `Cache-Control: no-store` and `X-Content-Type-Options: nosniff`.

Excluding free text and arbitrary metadata prevents a stored value from
becoming a spreadsheet formula. Export generation is synchronous because both
the time and row count are bounded. Asynchronous bulk-report infrastructure is
not introduced.

### Frontend boundary

The existing authenticated workspace adds a role-gated audit and reporting
area.

The frontend provides:

- authorized navigation only for the caller's server-provided roles;
- bounded audit filters and cursor navigation;
- loading, empty, error and result states;
- operational summary cards using exact integer values;
- report download controls with server error handling; and
- session-expiry recovery that clears audit and reporting query caches.

The client validates JSON responses at runtime and treats CSV as a download,
not executable content. Hidden navigation is usability only; backend service
authorization remains authoritative.

### Verification and delivery

Phase 10 is delivered in focused batches:

1. architecture, acceptance criteria and Phase 9 closure;
2. canonical audit domain, V19 persistence and atomic recording;
3. source-owned evidence readers and normalized audit search;
4. operational summaries and bounded CSV exports;
5. role-gated frontend workflow; and
6. documentation, cumulative PowerShell and Bash verification, and CI evidence.

Verification covers domain validation, PostgreSQL immutability and uniqueness,
transaction rollback, module boundaries, query ordering, cursor/filter
validation, role visibility, exact aggregates, CSV golden files, export limits,
sensitive-field exclusion, HTTP security, React workflows, lint and production
build.

## Consequences

### Positive

- Existing evidence retains one authoritative owner.
- New business events are recorded at mutation time instead of reconstructed
  later.
- Atomic recording prevents a successful mutation with missing required audit
  evidence.
- Normalization gives users one searchable history without cross-module
  repository access.
- Server-side visibility applies consistently to pages, aggregates and files.
- Strict time and row limits keep synchronous reporting predictable.
- Typed CSV schemas reduce data leakage and spreadsheet-injection risk.
- Reporting cannot alter financial or operational state.

### Negative

- Search combines several source models and needs explicit deterministic merge
  logic.
- Historical coverage differs by event family and must remain visible to users.
- Atomic audit recording adds work to existing mutation transactions.
- Exact report fixtures and CSV golden files increase test volume.
- Synchronous exports intentionally reject large requests instead of queuing
  them.

## Alternatives considered

### Copy every existing evidence row into one audit table

Rejected because it creates duplicate owners and fabricates canonical metadata
that was not captured when the original action occurred.

### Let the audit module read every domain repository

Rejected because it reverses ownership, creates broad coupling and would form
dependency cycles with modules that record new audit events.

### Build reports directly in controllers

Rejected because authorization, bounds, snapshot semantics and source ownership
must be enforced in reusable service and persistence boundaries.

### Export arbitrary JSON metadata

Rejected because arbitrary values can disclose sensitive data, destabilize the
schema and introduce spreadsheet-formula risk.

### Add asynchronous report jobs now

Rejected because the 31-day and 10,000-row limits make synchronous generation
bounded. Job infrastructure belongs only after a measured requirement.

### Use dashboards as observability

Rejected because business reporting and service telemetry answer different
questions. Logs, metrics, traces, load tests and SLOs remain Phase 11.

## Out of scope for Phase 10

- mutation or deletion of any audit evidence;
- financial corrections or ledger posting;
- accounting or regulatory reports;
- real-time streaming analytics;
- user-defined SQL, report columns or templates;
- exports over 31 days or 10,000 rows;
- arbitrary metadata or free-text export;
- asynchronous report jobs;
- data-warehouse, OLAP or search-cluster infrastructure;
- Kafka migration;
- production observability, load testing and SLOs;
- SIEM integration; and
- cryptographic audit-chain notarization.
