# Architecture diagrams

These diagrams describe the portfolio system at the level needed for review and interview discussion. They complement the detailed implementation notes in the repository README and architecture overview.

## System context

```mermaid
flowchart TD
    User["Customer, operations user, analyst or administrator"]
    Browser["Browser"]
    Platform["Payments and reconciliation platform"]
    Database["PostgreSQL system of record"]
    External["Synthetic settlement file"]
    User --> Browser
    Browser --> Platform
    Platform --> Database
    External --> Platform
```

The platform is an educational simulation. It does not connect to a bank, card scheme, clearing system or external payment processor.

## Release containers

```mermaid
flowchart TD
    Browser["Browser"]
    Nginx["Nginx frontend container"]
    Backend["Spring Boot backend container"]
    Postgres["PostgreSQL container"]
    Browser --> Nginx
    Nginx --> Backend
    Backend --> Postgres
```

Nginx serves the React build and proxies `/api/` requests to the backend. PostgreSQL health gates backend startup in the release composition. The containers run as unprivileged users.

## Payment submission and outbox boundary

```mermaid
sequenceDiagram
    participant B as Browser
    participant A as API
    participant I as Idempotency
    participant P as Payment
    participant L as Ledger
    participant O as Outbox
    B->>A: Submit payment with Idempotency-Key
    A->>I: Reserve or replay request
    I-->>A: New request or stored terminal response
    A->>P: Validate and process payment
    P->>L: Post balanced debit and credit
    P->>O: Record completed event in same transaction
    O-->>A: Commit payment, ledger and outbox atomically
    A-->>B: Deterministic response
```

The outbox event is created inside the financial transaction. Publication happens later, so an event cannot be successfully published for a payment whose transaction did not commit.

## Publication and consumer reliability

```mermaid
flowchart TD
    Pending["Pending outbox event"]
    Claimed["Owner-token publication lease"]
    Published["Published event"]
    Consumer["Checkpointed consumer"]
    Notification["Notification or consumer failure"]
    DeadLetter["Dead-letter operation"]
    Pending --> Claimed
    Claimed --> Published
    Published --> Consumer
    Consumer --> Notification
    Notification --> DeadLetter
    DeadLetter --> Published
```

Publication and notification delivery are at-least-once workflows. Unique source-event identifiers and durable checkpoints prevent a repeated delivery from creating a duplicate notification side effect.

## Settlement reconciliation

```mermaid
sequenceDiagram
    participant U as Analyst
    participant API as Reconciliation API
    participant R as Payment reader
    participant DB as Reconciliation store
    U->>API: Upload bounded UTF-8 CSV
    API->>API: Validate complete file before persistence
    API->>R: Read bounded payment snapshots
    R-->>API: Reconciliation-safe snapshots
    API->>DB: Persist import, rows, results and discrepancies atomically
    DB-->>U: Results and discrepancy evidence
    U->>API: Resolve discrepancy with If-Match
    API->>DB: Append one attributable resolution
```

Reconciliation never mutates payment, account, ledger or outbox history. A later authorised financial correction would need to be represented by an explicit compensating transaction.

## Payment lifecycle

```mermaid
stateDiagram-v2
    [*] --> PENDING
    PENDING --> PROCESSING
    PROCESSING --> COMPLETED
    PROCESSING --> REJECTED
    PROCESSING --> FAILED
    COMPLETED --> [*]
    REJECTED --> [*]
    FAILED --> [*]
```

Idempotency and stale-processing recovery constrain how repeated submissions interact with this lifecycle. The original posted ledger history remains append-only.