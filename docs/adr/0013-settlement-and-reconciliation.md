# ADR 0013: Add synthetic settlement imports and deterministic reconciliation

- Status: Accepted
- Date: 2026-07-24
- Decision owners: Project maintainer

## Context

Phases 5 through 8 established atomic internal payments, immutable
double-entry ledger postings, a transactional outbox, durable simulated
notifications and controlled outbox dead-letter recovery.

Phase 9 must demonstrate settlement and reconciliation without connecting to a
real bank, card scheme or clearing system. An authorised analyst must be able
to upload a bounded synthetic settlement file, inspect one result for every
row, review discrepancies and record an attributable resolution.

Settlement observations are not payment commands. Importing, matching or
resolving a settlement discrepancy must not silently change payment state,
account balances, ledger history or outbox events.

The generic audit module is still a declaration reserved for Phase 10.
Identity role-change evidence is private to the identity module and outbox
replay evidence is private to the outbox module. Phase 9 therefore owns its
immutable discrepancy-resolution decisions inside reconciliation.

## Decision

### Strict settlement CSV contract

The upload contains original raw bytes for exactly one UTF-8 CSV document. The
limits are:

- at most 1,048,576 raw bytes;
- between 1 and 1,000 data rows after one header row;
- no malformed UTF-8;
- no UTF-8 BOM;
- no NUL character;
- no blank records;
- no duplicate header or data-record identifiers; and
- bounded values in every field.

The exact header order is:

```text
settlement_record_id,payment_id,amount_minor_units,currency,settled_at
```

A standards-based CSV parser must handle quoted fields and escaped quotes.
Splitting physical lines or strings on commas is prohibited. A record must
contain exactly five fields. Missing, extra, duplicate or reordered columns are
invalid.

Field rules are:

- `settlement_record_id` contains 1 to 128 characters and matches
  `[A-Za-z0-9][A-Za-z0-9._:-]{0,127}`;
- `payment_id` is the lowercase hyphenated canonical text produced by
  `UUID.toString()`;
- `amount_minor_units` matches `[1-9][0-9]{0,18}` and parses without overflow
  to a positive Java `long`;
- `currency` is exactly `GBP`; and
- `settled_at` contains at most 40 characters, ends in `Z` and parses as a Java
  `Instant`.

Control characters and embedded line breaks are invalid in data fields even
when CSV quoting could represent them. CRLF and LF document line endings are
accepted because they do not alter normalised field values.

The controller reads the upload through a byte-capped stream instead of
trusting multipart metadata. The complete bounded byte array is decoded,
parsed, normalised and validated before a transaction that writes settlement
state begins. Any file-level or row-level validation failure creates no import.

### Raw-byte idempotency and external identifiers

The application computes lowercase SHA-256 over the original raw file bytes,
before decoding or newline normalisation. A unique fingerprint identifies one
accepted completed import.

Uploading identical accepted bytes again returns the existing import and
creates no rows, results or discrepancies. The initial creation response is
`201 Created`; an identical replay returns `200 OK` and identifies the response
as an existing import.

Different raw bytes are a different candidate import even if they normalise to
the same field values. A globally unique accepted
`settlement_record_id` prevents a different file from reusing an external
record. Reuse returns a stable `409 Conflict` and rolls back the entire
candidate import.

Fingerprint reservation uses a PostgreSQL uniqueness operation that tolerates
a concurrent identical upload. The losing request reads the completed import
after the winning transaction commits rather than treating the unique
fingerprint as an error.

### Reconciliation ownership and immutability

The reconciliation module owns:

- completed settlement-import metadata, fingerprint, filename, actor, time and
  final counts;
- immutable normalised settlement rows with original one-based data-row
  numbers;
- immutable reconciliation results;
- exclusive accepted-payment match claims;
- discrepancies and their `OPEN` or `RESOLVED` state; and
- immutable one-time discrepancy-resolution decisions.

Imported rows and results are append-only. PostgreSQL triggers reject update
and delete. Every committed imported row has exactly one committed result.
External record identifiers and `(import_id, row_number)` are unique.

The synchronous bounded workflow exposes no partially completed import. Import
metadata is externally visible only after the transaction has final counts and
commits.

### Public payment reconciliation reader

Reconciliation must not inject a payment repository, refer to a payment entity
or import payment-internal types.

The payment module exposes a narrow public batch reader. It accepts a
deduplicated set of at most 1,000 payment identifiers and returns at most one
snapshot per found identifier using one bounded database query. A snapshot
contains only:

- payment identifier;
- stable payment status;
- amount in minor units;
- currency;
- completion time, present only for `COMPLETED`; and
- linked ledger transaction identifier, present only for `COMPLETED`.

The existing payment `updated_at` is the completion time for a completed
payment because payment terminal states cannot transition again. The public
reader maps it to `completedAt` only for `COMPLETED` and does not expose
customer, account, idempotency or repository details.

The reconciliation module will add the public identity boundary to its declared
dependencies so the authenticated actor can be attributed. It will align its
declared dependencies to `identity`, `payment` and `shared`; it will not use
the placeholder audit module during Phase 9.

### Deterministic matching

Rows are evaluated in ascending original data-row order. Each accepted row
receives exactly one immutable result: `MATCHED` or `DISCREPANCY`.

The first applicable code wins in this exact order:

1. `PAYMENT_NOT_FOUND`;
2. `PAYMENT_NOT_COMPLETED`;
3. `CURRENCY_MISMATCH`;
4. `AMOUNT_MISMATCH`;
5. `SETTLED_BEFORE_COMPLETION`;
6. `DUPLICATE_PAYMENT_SETTLEMENT`; or
7. `MATCHED`.

The first five checks compare only the immutable row and public payment
snapshot. An otherwise valid row then attempts to claim the payment identifier
as its accepted settlement match.

Within one file, the lowest data-row number wins an otherwise-valid duplicate
claim. Across imports, the first committed accepted claim wins. PostgreSQL
uniqueness makes the invariant deterministic and race-safe: no two committed
rows can both be `MATCHED` for one payment. A later or concurrently losing
otherwise-valid row becomes `DUPLICATE_PAYMENT_SETTLEMENT`.

This commit-order rule is intentional. Choosing a winner independent of arrival
order would require revising an earlier immutable result or delaying all
reconciliation until a global ordering window closes.

Every non-match creates one discrepancy carrying the same primary code. Phase 9
does not add secondary codes.

### Atomic import transaction

After complete parsing and validation, one PostgreSQL transaction:

1. reserves the fingerprint or returns the existing completed import;
2. creates the import metadata;
3. persists every normalised immutable row;
4. reads all distinct payment snapshots through the bounded public batch
   reader;
5. evaluates rows in original data-row order;
6. acquires accepted-payment match claims;
7. stores exactly one result per row;
8. creates one discrepancy for every non-match; and
9. stores final row, matched and discrepancy counts before commit.

A persistence, uniqueness, trigger or deferred-constraint failure rolls back
the complete candidate import. No payment, account, ledger or outbox write is
part of this transaction.

### Discrepancy lifecycle and resolution evidence

A discrepancy state is `OPEN` or `RESOLVED`. A resolution decision is exactly
one of:

- `ACCEPTED`;
- `INTERNAL_CORRECTION_REQUIRED`; or
- `EXTERNAL_CORRECTION_REQUIRED`.

Resolution requires:

- authenticated `RECONCILIATION_ANALYST` or `ADMIN`;
- one non-blank trimmed reason of 1 to 500 characters without NUL or control
  characters;
- one strong version ETag supplied through `If-Match`; and
- an `OPEN` discrepancy.

The request body contains only the decision and reason; it does not duplicate
the expected version. Missing or malformed `If-Match` returns `428
Precondition Required` or a stable validation problem. A stale strong ETag
returns `412 Precondition Failed`.

One transaction locks the discrepancy, verifies the version, inserts an
immutable resolution decision and changes only the discrepancy status to
`RESOLVED`. The decision records:

- decision identifier;
- discrepancy identifier;
- actor identity-user identifier;
- decision;
- bounded reason;
- discrepancy version before resolution; and
- decision timestamp.

A unique discrepancy identifier prevents a second decision. A PostgreSQL
trigger rejects update and delete of decision evidence. Resolution never
changes the imported row, result, payment, account, ledger or outbox.
`INTERNAL_CORRECTION_REQUIRED` is a recorded instruction for a later authorised
compensating workflow, not a correction performed by Phase 9.

### HTTP, security and pagination

Import, import inspection, result inspection, discrepancy review and resolution
require `RECONCILIATION_ANALYST` or `ADMIN` at the service boundary.

Mutations use the existing PostgreSQL-backed browser session and CSRF
protection. All settlement import and discrepancy responses use
`Cache-Control: no-store`. Validation, authentication, authorisation,
precondition and conflict failures use the existing
`application/problem+json` conventions with stable codes.

Collections use bounded keyset pagination with a maximum page size of 100:

- import results advance by immutable data-row number; and
- discrepancy queues advance by creation time and discrepancy identifier.

Ordering and cursor semantics are part of the HTTP contract. Offset-only
pagination is not used for the discrepancy queue.

### Frontend analyst workflow

After the backend contract is stable, the React application adds role-gated:

- CSV selection and upload;
- import summary and matched/discrepancy counts;
- row-result inspection;
- discrepancy queue and detail;
- resolution form with the current ETag;
- stale-version recovery; and
- loading, empty, validation, error and success states.

Responses are runtime validated. TanStack Query keys are analyst-session scoped
and cleared on logout or session expiry. Mutations use the existing in-memory
CSRF handling. Vitest, Testing Library and MSW cover the workflow. No routing
dependency is added unless the existing workspace becomes materially
unmanageable.

## Consequences

### Positive

- The project demonstrates realistic bounded settlement ingestion without
  claiming a real clearing-system integration.
- Complete validation and one transaction prevent partial imports.
- Raw-byte idempotency makes retry behaviour exact and inspectable.
- Public batch snapshots preserve payment module ownership and avoid N+1
  queries.
- Immutable results and decisions provide attributable reconciliation evidence.
- Payment and ledger history remain untouched by reconciliation.

### Negative

- Strict CSV and canonical identifier rules reject some otherwise readable
  files.
- Real CSV parsing adds one small backend dependency.
- Database-backed exclusive match claims require a native conflict-aware
  persistence path.
- Commit order decides the winner between concurrently valid imports for the
  same payment.
- Keyset cursors and ETag handling add HTTP and frontend complexity.

## Rejected alternatives

### Split CSV text on commas or physical lines

Rejected because quoting, escaped quotes and embedded delimiters make naive
splitting incorrect and potentially unsafe.

### Persist valid rows from an otherwise invalid file

Rejected because partial import semantics weaken idempotency, counts and
analyst trust.

### Read payment repositories from reconciliation

Rejected because it bypasses the executable module boundary and couples
reconciliation to payment persistence.

### Match directly against ledger internals

Rejected because settlement rows identify payments, while the payment module
already owns the safe link to an immutable ledger transaction.

### Put resolution evidence in the placeholder audit module

Rejected because Phase 10 owns the general audit capability. Phase 9 must not
claim or couple to an audit implementation that does not exist.

### Mutate payment or ledger history during resolution

Rejected because a discrepancy decision is evidence, not a financial posting.
A correction must be an explicit later compensating transaction.

## Out of scope for Phase 9

- real bank, card-scheme or clearing-system connectivity;
- non-GBP settlement;
- multiple CSV schemas or spreadsheet uploads;
- partial import acceptance;
- editing or deleting imported rows, results or resolution evidence;
- automatic financial corrections;
- general business-audit search;
- reporting exports;
- Kafka settlement ingestion;
- unbounded bulk operations; and
- production malware scanning or content-disarm infrastructure.
