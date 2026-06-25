# Architecture overview

## Architectural style

The application begins as a modular monolith with a separately built browser
client.

The backend is one deployable Spring Boot process, but its business domains are
treated as independently owned modules.

Spring Modulith verifies declared module boundaries during automated testing.

Asynchronous infrastructure is introduced only when an implemented use case
requires durable asynchronous delivery.

## Implementation status

### Phase 1 — Executable platform foundation

Phase 1 established:

- the Spring Boot modular monolith;
- PostgreSQL and Flyway integration;
- the React and TypeScript browser client;
- the versioned system-information API;
- Actuator and OpenAPI;
- correlation-ID propagation;
- Testcontainers integration testing; and
- GitHub Actions verification.

### Phase 2 — Identity and access

Phase 2 implemented:

- persistent identity users and roles;
- normalised and uniquely constrained email addresses;
- password validation and BCrypt hashing;
- customer registration;
- Spring Security authentication;
- PostgreSQL-backed browser sessions;
- CSRF protection;
- failed-login tracking and temporary lockout;
- service-level role-based access control;
- administrator role management;
- session invalidation following role changes; and
- immutable role-change security audit events.

The remaining payment, ledger, customer, account, reconciliation,
notification, reporting and operational capabilities are introduced in later
phases.

The Kafka-compatible broker, asynchronous consumers and observability stack
also remain planned components.
## C4 context diagram

```mermaid
flowchart LR
    customer["Customer User"]
    operations["Operations User"]
    analyst["Reconciliation Analyst"]
    administrator["Administrator"]
    settlementProvider["Simulated Settlement Provider"]
    notificationSink["Simulated Notification Sink"]

    platform["Event-Driven Payments and Reconciliation Platform<br/>Educational system — no real money"]

    customer -->|"Views accounts and submits payments"| platform
    operations -->|"Manages customers and investigates payments"| platform
    analyst -->|"Uploads settlement files and reviews discrepancies"| platform
    administrator -->|"Manages access and operational recovery"| platform
    settlementProvider -->|"Provides synthetic settlement files"| platform
    platform -->|"Produces simulated notifications"| notificationSink
```

## C4 container diagram

```mermaid
flowchart LR
    user["Authorised User"]

    subgraph platform["Payments and Reconciliation Platform"]
        web["React SPA<br/>TypeScript and Vite"]
        api["Modular Monolith<br/>Java, Spring Boot and Spring Modulith"]
        database[("PostgreSQL<br/>System of record")]
        broker[("Kafka-compatible broker<br/>Introduced in Phase 7")]
        consumer["Asynchronous Consumer<br/>Extracted only when justified"]
        telemetry["Observability Stack<br/>Introduced in Phase 11"]
    end

    user -->|"HTTPS"| web
    web -->|"JSON API and secure session cookie"| api
    api -->|"Transactions and queries"| database
    api -.->|"Outbox publication"| broker
    broker -.->|"At-least-once events"| consumer
    consumer -->|"Consumer state and audit writes"| database
    api -.->|"Logs, metrics and traces"| telemetry
    consumer -.->|"Logs, metrics and traces"| telemetry
```

Dashed relationships represent later phases and are not part of the initial
runtime.

## Backend module map

```mermaid
flowchart TB
    identity["identity"]
    customer["customer"]
    account["account"]
    payment["payment"]
    ledger["ledger"]
    risk["risk"]
    reconciliation["reconciliation"]
    notification["notification"]
    audit["audit"]
    reporting["reporting"]
    shared["shared"]

    identity --> shared
    customer --> shared
    account --> customer
    account --> shared
    payment --> account
    payment --> ledger
    payment --> risk
    payment --> audit
    ledger --> shared
    reconciliation --> payment
    reconciliation --> audit
    notification -.-> payment
    notification --> audit
    reporting --> account
    reporting --> payment
    reporting --> reconciliation
    reporting --> audit
```

The diagram represents conceptual dependencies.

Phase 1 introduced declared module metadata and an automated Spring Modulith
verification test. Public module APIs and internal implementations will be
introduced with each domain capability.

## Module responsibilities

### Identity

Owns:

- users;
- credentials;
- roles;
- authentication;
- session-related application behaviour; and
- security audit events.

It does not own customer account balances.

### Customer

Owns:

- customer profiles;
- customer identifiers; and
- customer lifecycle.

### Account

Owns:

- customer accounts;
- account ownership;
- account status;
- account balance snapshots; and
- optimistic versions.

### Payment

Owns:

- payment requests;
- payment state;
- payment orchestration;
- idempotency records; and
- payment-related events.

### Ledger

Owns:

- ledger accounts;
- ledger transaction headers;
- immutable debit entries;
- immutable credit entries; and
- balance-verification queries.

### Risk

Owns deterministic payment-validation rules.

Version 1 does not claim to implement regulated fraud detection.

### Reconciliation

Owns:

- settlement imports;
- imported settlement rows;
- reconciliation runs;
- matching results;
- discrepancies; and
- discrepancy resolution.

### Notification

Owns:

- simulated notification delivery;
- notification attempts;
- retry state; and
- consumer deduplication.

### Audit

Owns immutable business and security audit events.

### Reporting

Owns:

- operational read queries;
- dashboard projections; and
- demonstration report exports.

Reporting code cannot mutate financial records.

### Shared

Contains a deliberately small set of cross-cutting technical primitives such
as:

- identifiers;
- clock access;
- correlation metadata;
- API error structures; and
- supported currency primitives.

It must not become a generic dumping ground.

## Strong consistency boundaries

The following operations use one PostgreSQL transaction.

### Payment posting

The payment transaction will:

1. reserve or load the idempotency record;
2. validate the source and destination accounts;
3. validate the source balance;
4. progress the payment state;
5. create the ledger transaction;
6. create balanced ledger entries;
7. update account balance snapshots;
8. create the audit event;
9. create the outbox event; and
10. store the idempotent response.

If any required operation fails, the transaction rolls back.

### Role change

A role change transaction:

1. modifies the role assignment;
2. writes the associated immutable security audit event; and
3. invalidates active sessions for the affected user.

The role mutation and security event commit atomically.
### Discrepancy resolution

A discrepancy-resolution transaction will:

1. update the resolution state; and
2. write the associated audit event.

## Eventual consistency boundaries

The following may temporarily lag behind a committed payment:

- notification delivery;
- operational reporting projections;
- broker-delivery status;
- external settlement availability; and
- reconciliation results.

A delayed notification must never imply that a committed payment was lost.

## Payment and settlement separation

Payment processing and settlement reconciliation are separate state machines.

A payment can be completed internally while remaining unreconciled externally.

A reconciliation discrepancy does not mutate or delete the original ledger
transaction.

A financial correction requires a new compensating ledger transaction.

## Persistence principles

### Implemented by Phase 2

- PostgreSQL is the application system of record.
- Flyway owns forward-only schema migration history.
- Hibernate schema generation is disabled.
- Migration version 1 establishes the baseline.
- Migration version 2 creates the identity schema.
- Migration version 3 creates the JDBC session schema.
- Migration version 4 creates the identity security-event log.
- Identity email uniqueness is protected by a database constraint.
- Browser sessions are stored in PostgreSQL.
- Role-change security events are append-only.
- A PostgreSQL trigger rejects updates and deletions of security-event rows.
- Database integration is tested with real PostgreSQL Testcontainers.

### Planned domain persistence guarantees

- Ledger entries are append-only.
- Account balances are transactionally maintained snapshots.
- Ledger-derived balances can be recalculated for verification.
- Outbox records retain diagnostic and retry metadata.
- Imported files retain synthetic source identifiers and fingerprints.
- Financial schema changes use forward-only Flyway migrations.
## API principles

### Implemented by Phase 2

- APIs are versioned under `/api/v1`.
- `GET /api/v1/system/info` exposes non-sensitive platform metadata.
- Correlation identifiers are propagated through `X-Correlation-ID`.
- Customer registration validates bounded email and password inputs.
- Authentication uses a server-side PostgreSQL session.
- State-changing browser requests require CSRF protection.
- Session responses use `Cache-Control: no-store`.
- Role-management operations are authorised at the service boundary.
- Anonymous protected requests return `401 Unauthorized`.
- Authenticated users without sufficient authority receive `403 Forbidden`.
- Actuator exposes health, liveness and readiness.
- OpenAPI output is available under `/v3/api-docs`.

### Planned API guarantees

- All errors use a consistent `application/problem+json` structure.
- Field errors use stable property paths.
- Business conflicts use stable machine-readable codes.
- Pagination is bounded.
- Payment submission requires an `Idempotency-Key` header.
## Initial risk register

| Risk | Consequence | Control |
|---|---|---|
| Ledger imbalance | Financial corruption | Domain checks, deferred trigger and invariant tests |
| Concurrent debit | Negative account balance | Optimistic locking and business-rule re-evaluation |
| Request replay | Duplicate payment | Idempotency key, fingerprint and unique constraint |
| Lost event | Missing downstream action | Transactional outbox |
| Duplicate event | Repeated side effect | Consumer deduplication |
| Invalid state change | Inconsistent payment lifecycle | Explicit state machine |
| Broken access control | Data disclosure or mutation | Service-level RBAC and ownership tests |
| Malicious import | Injection or resource exhaustion | Limits, streaming and strict parsing |
| Sensitive logging | Credential or data exposure | Allow-listed fields and redaction tests |
| Framework incompatibility | Build or runtime failure | Locked versions and compatibility tests |
| Premature distribution | Excessive operational complexity | Modular monolith and extraction criteria |
| Misleading claims | Portfolio credibility damage | Educational disclaimer and measured evidence |

## Planned documentation

The final documentation set will include:

- C4 context diagram;
- C4 container diagram;
- module diagram;
- entity-relationship diagram;
- payment-submission sequence diagram;
- outbox-publication sequence diagram;
- reconciliation sequence diagram;
- payment state machine;
- account state machine;
- consistency-boundary explanation;
- idempotency explanation;
- transactional-outbox explanation;
- API examples;
- threat model;
- performance methodology;
- measured performance results;
- documented limitations; and
- a five-minute interview demonstration.
