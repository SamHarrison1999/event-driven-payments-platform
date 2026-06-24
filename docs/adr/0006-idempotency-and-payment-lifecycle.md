# ADR 0006: Idempotency and payment lifecycle

- Status: Accepted
- Date: 2026-06-24
- Decision owners: Project maintainer

## Context

Clients may retry a request after a timeout even when the original payment
successfully committed.

Repeated clicks, network retries and concurrent submissions must not create
additional payments.

Payment status changes must also be explicit, restricted and testable.

## Decision

## Idempotency scope

Payment submission requires an `Idempotency-Key` request header.

A stored idempotency identity consists of:

```text
authenticated actor + operation name + idempotency key
```

The idempotency record stores:

-   a canonical request fingerprint;
-   processing state;
-   resulting payment identifier;
-   HTTP response status;
-   serialised response;
-   creation time; and
-   retention metadata.

A unique database constraint protects the scope and key.

Request behaviour
-----------------

### New key

Reserve the idempotency key and process the request.

### Existing key with the same fingerprint and completed result

Return the stored result without posting another payment.

### Existing key with a different fingerprint

Return `409 Conflict` with error code:

```
IDEMPOTENCY_KEY_REUSED
```

### Existing key still being processed

Return `409 Conflict` with error code:

```
IDEMPOTENCY_REQUEST_IN_PROGRESS
```

The client may retry later using the same key and unchanged request.

Canonical fingerprint
---------------------

The fingerprint is derived from a canonical server-side representation of the\
meaningful request fields.

It excludes transport-only metadata such as:

-   correlation identifiers;
-   tracing headers;
-   user-agent values; and
-   request timestamps.

The server does not trust a fingerprint supplied by the client.

Payment state machine
---------------------

Version 1 payment states are:

-   `PENDING`;
-   `PROCESSING`;
-   `COMPLETED`;
-   `REJECTED`; and
-   `FAILED`.

Permitted transitions are:

```
PENDING -> PROCESSINGPROCESSING -> COMPLETEDPROCESSING -> REJECTEDPROCESSING -> FAILED
```

Terminal states cannot transition to another payment state.

A compensating payment is a new payment and does not reopen a completed\
payment.

`REJECTED` represents a deterministic business refusal such as insufficient\
funds or an inactive account.

`FAILED` represents an unexpected processing failure that is safely recorded\
without a committed ledger posting.

Settlement and reconciliation status are modelled separately.

Consequences
------------

### Positive

-   Client retries are safe.
-   Duplicate clicks do not create duplicate payments.
-   Same-key and different-request errors are visible.
-   Concurrent duplicate requests are database-protected.
-   Payment transitions can be tested exhaustively.

### Negative

-   Responses require bounded persistence.
-   Request canonicalisation must remain stable.
-   Retention policy must balance replay protection and storage.
-   In-progress request recovery requires explicit handling.

Revisit triggers
----------------

Review retention periods and recovery behaviour after load and failure testing\
produce evidence.

Changing the payment state machine requires an ADR amendment and database\
migration analysis.
