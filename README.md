# Event-Driven Payments and Reconciliation Platform

A portfolio-quality simulation of payment processing, double-entry ledgering,
asynchronous event delivery and settlement reconciliation.

> **Educational system:** this application does not process real money and
> must not be used as a banking, payment-processing or accounting product.

## Project status

Current phase:

**Phase 9 — Settlement and reconciliation**

Phase 8 is complete and merged through PR #8 at merge commit `179d793`.
Its remote feature branch has been removed. The stable main branch now includes
durable notifications, customer-owned notification queries and
administrator-only outbox dead-letter recovery with immutable replay evidence.

Phase 9 settlement and reconciliation implementation and cumulative local
verification are complete on its feature branch. PR #9 passed the required
repository, backend and frontend checks at `a548c68` and is awaiting merge.
ADR 0013 defines the strict synthetic CSV contract, raw-byte import
idempotency, bounded payment-read boundary, deterministic discrepancy
classification, atomic persistence boundary and immutable resolution evidence.

The `main` branch remains protected by a ruleset requiring pull requests and
the repository, backend and frontend CI checks.

See the [progress ledger](docs/progress/ledger.md) for the verified project
status.
## Implemented capabilities

### Backend

The backend currently provides:

- a Java 25 and Spring Boot 4 modular-monolith foundation;
- Spring Modulith module declarations and architecture verification;
- PostgreSQL connectivity;
- Flyway-managed database migrations;
- Testcontainers-based PostgreSQL integration tests;
- a versioned system-information endpoint;
- Spring Boot Actuator health endpoints;
- OpenAPI documentation and Swagger UI;
- request correlation identifiers; and
- an executable Spring Boot JAR.

The implemented API endpoint is:

```text
GET /api/v1/system/info
```

Operational endpoints include:

```text
GET /actuator/health
GET /actuator/health/liveness
GET /actuator/health/readiness
GET /v3/api-docs
GET /swagger-ui.html
```

### Identity and access

The backend identity module currently provides:

- normalised, uniquely constrained email addresses;
- a bounded password policy;
- BCrypt password hashing through Spring Security;
- customer registration with duplicate-email protection;
- server-side PostgreSQL-backed browser sessions;
- CSRF token delivery and CSRF-protected state-changing requests;
- secure session-cookie configuration;
- failed-login tracking and temporary account lockout;
- `CUSTOMER`, `OPERATIONS`, `RECONCILIATION_ANALYST` and `ADMIN` roles;
- administrator-only role management;
- active-session invalidation after role changes; and
- immutable security audit events for role assignments.

Implemented identity endpoints include:

    GET    /api/v1/identity/csrf
    POST   /api/v1/identity/registrations
    POST   /api/v1/identity/session
    GET    /api/v1/identity/session
    DELETE /api/v1/identity/session
    PUT    /api/v1/identity/users/{userId}/roles/{role}
    DELETE /api/v1/identity/users/{userId}/roles/{role}

Role-management endpoints require the `ADMIN` role.

### Customers and accounts

The backend customer and account modules currently provide:

- simulated customer profiles with `ACTIVE`, `SUSPENDED` and `CLOSED`
  lifecycle states;
- operations and administrator customer management;
- identity-to-customer assignments;
- one customer shared by multiple assigned identities;
- customer-owned account queries based on the authenticated principal;
- GBP-only accounts represented in integer minor units;
- account lifecycle states for active, frozen and closed accounts;
- zero-overdraft and funded-account closure protection;
- optimistic concurrency through strong ETags and required `If-Match`
  preconditions;
- strict request-body and identifier validation;
- database constraints for customer, account and ownership invariants; and
- consistent `application/problem+json` responses for authentication,
  authorisation, validation and business conflicts.

Implemented customer and account endpoints include:

    POST /api/v1/customers
    GET  /api/v1/customers/{customerId}
    PUT  /api/v1/customers/{customerId}/name
    PUT  /api/v1/customers/{customerId}/status
    PUT  /api/v1/customers/{customerId}/identity-users/{identityUserId}
    GET  /api/v1/customers/{customerId}/accounts
    POST /api/v1/accounts
    GET  /api/v1/accounts
    GET  /api/v1/accounts/{accountId}
    PUT  /api/v1/accounts/{accountId}/status

Customer and account management requires the `OPERATIONS` or `ADMIN` role.
The customer account collection is resolved from the authenticated identity
and does not accept a caller-supplied customer identifier.

### Double-entry ledger

The backend ledger module currently provides:

- immutable transaction headers with two or more ordered entries;
- positive GBP amounts represented as exact integer minor units;
- explicit `DEBIT` and `CREDIT` posting sides;
- application-level rejection of incomplete, one-sided and unbalanced
  journals;
- atomic persistence of each transaction header and all entries;
- deferred PostgreSQL balance verification at transaction commit;
- database triggers that reject updates and deletes of posted records;
- compensating-transaction links without mutation of original history;
- transaction lookup with deterministic entry ordering;
- deterministic account-entry history; and
- verification of account snapshots against ledger debit and credit totals.

The ledger capability remains exposed as a public Java module API and is now
used by the Phase 5 payment transaction. This educational application still
does not process real money.

### Synchronous payments

The payment module currently provides:

- authenticated internal GBP payment submission;
- source-account ownership derived from the authenticated identity;
- durable, identity-scoped `Idempotency-Key` reservation;
- canonical SHA-256 request fingerprints;
- exact replay of stored terminal HTTP responses for 24 hours;
- stale processing-lease recovery;
- explicit `PENDING`, `PROCESSING`, `COMPLETED`, `REJECTED` and `FAILED`
  payment states;
- atomic source debit, destination credit, balanced ledger posting and payment
  completion;
- deterministic and replayable business rejection responses;
- bounded whole-transaction concurrency retries;
- failure finalisation without exposing internal exception details; and
- ownership-aware payment lookup.

Implemented payment endpoints include:

    POST /api/v1/payments
    GET  /api/v1/payments/{paymentId}

Submission requires the `CUSTOMER` role and an `Idempotency-Key` header.
Customers may read only payments they submitted. `OPERATIONS` and `ADMIN` may
read any payment for investigation, while `RECONCILIATION_ANALYST` has no
Phase 5 payment-read permission.

### Transactional outbox

The outbox module currently provides:

- Flyway-managed event, payload and publication metadata in PostgreSQL;
- one stable `payment.completed.v1` JSON event for each completed payment;
- atomic event creation inside the account, ledger, payment and idempotency
  transaction;
- no completed-payment event for rejected or failed payments;
- bounded `FOR UPDATE SKIP LOCKED` batch claiming;
- owner-token publication leases with expired-lease recovery;
- a simulated logging transport that does not require broker infrastructure;
- successful transition from `PENDING` through `PUBLISHING` to `PUBLISHED`;
- bounded exponential retry scheduling with deterministic jitter;
- transition to `DEAD_LETTER` after permanent or exhausted failure; and
- PostgreSQL and domain verification of payload and lifecycle constraints.

Publication is at least once. Duplicate delivery remains possible after lease
recovery, so consumers implement idempotency through a unique source-event
identifier.

### Notifications and dead-letter operations

The notification and outbox modules now provide:

- stable published-event pages ordered by publication time and event identifier;
- a durable consumer checkpoint advanced in the same PostgreSQL transaction as
  notification or terminal consumer-failure persistence;
- strict `payment.completed.v1` schema-version-1 projection;
- unique source-event deduplication;
- inspectable failures for invalid supported payloads;
- notification lifecycle states `PENDING`, `DELIVERING`, `DELIVERED` and
  `DEAD_LETTER`;
- bounded notification claims using `FOR UPDATE SKIP LOCKED`, owner tokens and
  expiring leases;
- bounded retry scheduling with deterministic jitter;
- customer-owned notification history derived from the authenticated identity;
- administrator-only outbox dead-letter inspection;
- controlled replay only from `DEAD_LETTER`;
- immutable payload preservation, replay metadata and replay-audit evidence; and
- PostgreSQL, method-security, HTTP and frontend workflow verification.

Implemented Phase 8 endpoints include:

    GET  /api/v1/notifications
    GET  /api/v1/admin/outbox/dead-letters
    POST /api/v1/admin/outbox/dead-letters/{eventId}/replay

The customer notification endpoint requires `CUSTOMER`. Dead-letter inspection
and replay require `ADMIN`; replay is also CSRF protected.

### Settlement and reconciliation

The reconciliation module now provides a role-gated synthetic settlement
workflow without connecting to a bank, card scheme or clearing system:

- one strict UTF-8 CSV contract capped at 1 MiB and 1,000 data rows;
- complete parsing and validation before any settlement persistence;
- raw-byte SHA-256 idempotency for identical accepted uploads;
- immutable imported rows and one immutable reconciliation result per row;
- a narrow public payment-module batch reader with no payment repository access;
- ordered discrepancy codes and one database-protected accepted match per
  payment;
- atomic import, row, result, discrepancy and final-count persistence;
- analyst and administrator inspection with bounded keyset pagination;
- strong ETags and required `If-Match` for one-time discrepancy resolution; and
- immutable actor, decision, reason and timestamp evidence owned by the
  reconciliation module.

Reconciliation state is separate from payment processing. Import, matching or
resolution never changes a payment, account balance, ledger transaction, ledger
entry or outbox event. A later authorised correction workflow must use an
explicit compensating ledger transaction.

Implemented Phase 9 endpoints include:

    POST /api/v1/settlement-imports
    GET  /api/v1/settlement-imports/{importId}/results
    GET  /api/v1/settlement-discrepancies
    GET  /api/v1/settlement-discrepancies/{discrepancyId}
    PUT  /api/v1/settlement-discrepancies/{discrepancyId}/resolution

All settlement and discrepancy endpoints require
`RECONCILIATION_ANALYST` or `ADMIN`. Upload and resolution mutations are CSRF
protected, and resolution also requires the current strong `If-Match` ETag.

### Frontend

The frontend currently provides:

- a React and TypeScript application built with Vite;
- TanStack Query server-state management and customer-scoped cache isolation;
- a typed authenticated API client with runtime JSON and problem validation;
- server-side session bootstrap, login, logout and CSRF-protected mutations;
- customer-owned account and exact GBP balance presentation;
- direct GBP text-to-minor-unit conversion without floating-point arithmetic;
- reviewable payment drafts with retry-safe idempotent submission;
- bounded session-storage recovery for one unresolved payment request;
- accessible completed, rejected, failed and in-progress payment outcomes;
- customer-owned payment lookup with privacy-preserving unavailable results;
- customer-owned simulated payment notifications with loading, empty, error and
  delivered states;
- administrator-only outbox dead-letter inspection, payload review and
  CSRF-protected replay;
- optimistic replay-conflict handling and replay-success feedback;
- analyst and administrator settlement-file upload with atomic import results;
- open and resolved discrepancy queues with bounded pagination;
- strong-ETag discrepancy resolution and stale-version recovery;
- session-expiry recovery that clears customer, administrator and
  reconciliation query caches while preserving safe payment retry state;
- Vitest, Testing Library and Mock Service Worker workflow tests; and
- ESLint static analysis and Vite production builds.

### Delivery

The project currently provides:

- a PostgreSQL Docker Compose service;
- locked backend and frontend dependencies;
- cumulative PowerShell and Bash verification scripts through Phase 9; and
- GitHub Actions jobs for repository, backend and frontend verification.

## Local development

### Prerequisites

The verified Windows development environment uses:

- Java 25;
- Docker Desktop;
- Node.js 24.18.0;
- pnpm 11.9.0; and
- PowerShell 5.1 or later.

### Start PostgreSQL

From the repository root:

```powershell
docker compose up -d postgres
docker compose ps
```

Wait until PostgreSQL reports a healthy status.

### Start the backend

In a second terminal:

```powershell
cd backend
.\gradlew.bat bootRun
```

The backend starts at:

```text
http://localhost:8080
```

### Start the frontend

In a third terminal:

```powershell
cd frontend
pnpm install --frozen-lockfile
pnpm dev
```

The frontend starts at:

```text
http://localhost:5173
```

During local development, Vite proxies API requests to the Spring Boot
backend.

### Stop the services

Stop the frontend and backend with `Ctrl+C`.

Then, from the repository root:

```powershell
docker compose down
```

This preserves the PostgreSQL development volume.

## Phase 1 verification

### Windows PowerShell

From the repository root:

```powershell
.\scripts\verify-phase-1.ps1
```

A successful run ends with:

```text
Phase 1 verification passed.
```

The verifier:

1. checks required repository files;
2. validates the Docker Compose configuration;
3. tests and packages the backend;
4. installs locked frontend dependencies;
5. runs frontend linting;
6. runs frontend tests;
7. creates the frontend production build; and
8. checks staged and unstaged whitespace.

### Bash

On a Linux, macOS or WSL environment containing the complete Java, Docker,
Node.js and pnpm toolchain:

```bash
./scripts/verify-phase-1.sh
```

The Bash script performs the same Phase 1 verification gate.

## Phase 2 verification

### Windows PowerShell

From the repository root:

```powershell
.\scripts\verify-phase-2.ps1
```

A successful run ends with:

```text
Phase 2 verification passed.
```

The Phase 2 verifier checks the identity implementation, documentation and
Flyway migration sequence before running the complete Phase 1 baseline
verification.

### Bash

On Linux, macOS or WSL:

```bash
./scripts/verify-phase-2.sh
```

## Phase 3 verification

### Windows PowerShell

From the repository root:

```powershell
.\scripts\verify-phase-3.ps1
```

A successful run ends with:

```text
Phase 3 verification passed.
```

The Phase 3 verifier checks the customer and account implementation,
documentation, database migrations and security hardening before running the
complete Phase 2 baseline verification.

### Bash

On Linux, macOS or WSL:

```bash
./scripts/verify-phase-3.sh
```

## Phase 4 verification

### Windows PowerShell

From the repository root:

```powershell
.\scripts\verify-phase-4.ps1
```

A successful run ends with:

```text
Phase 4 verification passed.
```

The Phase 4 verifier checks the ledger domain, persistence, PostgreSQL
invariants, posting and query implementation, documentation and Flyway
migration sequence before running the complete Phase 3 baseline verification.

### Bash

On Linux, macOS or WSL:

```bash
./scripts/verify-phase-4.sh
```

## Phase 5 verification

### Windows PowerShell

From the repository root:

```powershell
.\scripts\verify-phase-5.ps1
```

A successful run ends with:

```text
Phase 5 verification passed.
```

The Phase 5 verifier checks the payment domain, account-module payment
boundary, persistence and migration sequence, idempotency reservation and
replay, synchronous submission and lookup APIs, documentation and the complete
Phase 4 baseline verification.

### Bash

On Linux, macOS or WSL:

```bash
./scripts/verify-phase-5.sh
```

## Phase 6 verification

### Windows PowerShell

From the repository root:

```powershell
.\scripts\verify-phase-6.ps1
```

A successful run ends with:

```text
Phase 6 verification passed.
```

The Phase 6 verifier checks the authenticated payment workspace, customer
session and cache isolation, exact GBP handling, idempotent submission,
accessible payment outcomes, customer-owned payment lookup and documentation.
It then runs the complete Phase 5 baseline, which performs backend verification
and executes frontend dependency installation, lint, all frontend tests and the
production build once.

### Bash

On Linux, macOS or WSL:

```bash
./scripts/verify-phase-6.sh
```
## Phase 7 verification

### Windows PowerShell

From the repository root:

```powershell
.\scripts\verify-phase-7.ps1
```

A successful run ends with:

```text
Phase 7 verification passed.
```

The Phase 7 verifier checks the outbox schema and lifecycle, stable
`payment.completed.v1` contract, atomic payment-event creation, claiming leases,
retry and dead-letter implementation, migration sequence and documentation. It
then runs the complete Phase 6 baseline, which executes the backend and frontend
regression gate once.

### Bash

On Linux, macOS or WSL:

```bash
./scripts/verify-phase-7.sh
```
## Architecture


The application is structured as a modular monolith with a separately built
browser client.

The backend currently declares the following modules:

- identity;
- customer;
- account;
- payment;
- ledger;
- risk;
- reconciliation;
- notification;
- audit;
- reporting;
- outbox; and
- shared.

Business functionality will be introduced incrementally without bypassing the
declared module boundaries.

The architecture is described in
[docs/architecture/overview.md](docs/architecture/overview.md).

Major decisions are recorded as Architecture Decision Records under
[docs/adr](docs/adr).

## Current technology baseline

| Technology | Resolved version |
|---|---:|
| Java | 25.0.1 LTS |
| Spring Boot | 4.0.7 |
| Spring Modulith | 2.0.7 |
| Springdoc OpenAPI | 3.0.3 |
| Gradle Wrapper | 9.6.0 |
| Flyway | 11.14.1 |
| PostgreSQL | 18.4 |
| PostgreSQL JDBC | 42.7.11 |
| Testcontainers | 2.0.5 |
| Node.js | 24.18.0 |
| pnpm | 11.9.0 |
| React | 19.2.7 |
| TypeScript | 6.0.3 |
| Vite | 8.1.0 |
| TanStack Query | 5.101.1 |
| Vitest | 4.1.9 |
| Mock Service Worker | 2.14.6 |

Dependencies managed by Spring Boot will not be independently overridden
without a documented reason.

## Repository layout

```text
backend/          Spring Boot modular monolith
frontend/         React and TypeScript application
infrastructure/   Reserved for later deployment infrastructure
load-tests/       Reserved for later performance tests
docs/             Architecture, product and project evidence
scripts/          Reproducible verification commands
.github/          Continuous-integration workflows
```

## Product objective

The completed platform is intended to allow authorised users to:

- register and authenticate;
- create simulated customers and accounts;
- view account balances and transaction history;
- submit internal account-to-account payments;
- track payment status;
- upload synthetic settlement files;
- reconcile settlement records against internal ledger entries;
- review discrepancies;
- inspect immutable audit history;
- view operational metrics; and
- receive simulated payment notifications.

Identity registration, authentication and access management are implemented.
Customer profiles, GBP accounts, ownership views, account lifecycle management,
the immutable double-entry ledger, synchronous payment submission,
ownership-aware payment lookup and frontend payment flows are also implemented.
The transactional outbox, durable simulated notifications and controlled
dead-letter recovery are implemented. Settlement and reconciliation are the
current phase; audit and reporting, observability, security hardening and
release infrastructure remain planned work.

## Engineering principles

1. Financial correctness takes priority over implementation convenience.
2. Database constraints complement application-level validation.
3. Monetary values never use binary floating-point arithmetic.
4. Important business changes and their outbox events commit atomically.
5. Event consumers assume at-least-once delivery.
6. Module boundaries are executable architecture rules.
7. Security behaviour is tested rather than merely configured.
8. Documentation distinguishes implemented behaviour from planned behaviour.
9. Performance claims must be supported by reproducible measurements.
10. The system never claims to process real money.

## Documentation

- [Product scope](docs/product/scope.md)
- [Product backlog](docs/product/backlog.md)
- [Definition of done](docs/product/definition-of-done.md)
- [Architecture overview](docs/architecture/overview.md)
- [Progress ledger](docs/progress/ledger.md)
- [Security policy](SECURITY.md)

## Licence

This project is licensed under the MIT License.
### Start the frontend

In a third terminal:

```powershell
cd frontend
pnpm install --frozen-lockfile
pnpm dev
```

### Verification gates

The cumulative verifiers retain the earlier acceptance gates:

- Phase 2 verification: `scripts/verify-phase-2.ps1` and
  `scripts/verify-phase-2.sh`;
- Phase 3 verification: `scripts/verify-phase-3.ps1` and
  `scripts/verify-phase-3.sh`;
- Phase 4 verification: `scripts/verify-phase-4.ps1` and
  `scripts/verify-phase-4.sh`;
- Phase 5 verification: `scripts/verify-phase-5.ps1` and
  `scripts/verify-phase-5.sh`;
- Phase 6 verification: `scripts/verify-phase-6.ps1` and
  `scripts/verify-phase-6.sh`; and
- Phase 7 verification: `scripts/verify-phase-7.ps1` and
  `scripts/verify-phase-7.sh`;
- Phase 8 verification: `scripts/verify-phase-8.ps1` and
  `scripts/verify-phase-8.sh`; and
- Phase 9 verification: `scripts/verify-phase-9.ps1` and
  `scripts/verify-phase-9.sh`.

The Phase 6 gate verifies the authenticated payment workspace and
customer-owned payment lookup. The later gates retain that complete baseline.

### Phase 8 verification

From the repository root on Windows:

```powershell
powershell.exe -NoProfile -ExecutionPolicy Bypass -File .\scripts\verify-phase-8.ps1
```

From WSL or another Bash environment:

```bash
bash scripts/verify-phase-8.sh
```

The Phase 8 verifier checks the notification and replay contracts, Flyway
migrations 1 through 15, documentation and required files. It then runs the
complete Phase 7 baseline. That baseline recursively executes the full backend
`clean test bootJar` gate, frozen frontend install, ESLint, all Vitest tests,
the Vite production build and repository whitespace checks.

### Phase 9 verification

From the repository root on Windows:

```powershell
powershell.exe -NoProfile -ExecutionPolicy Bypass -File .\scripts\verify-phase-9.ps1
```

From WSL or another Bash environment:

```bash
bash scripts/verify-phase-9.sh
```

The Phase 9 verifier checks the settlement import, deterministic matching,
database immutability, discrepancy-resolution, security, frontend and
documentation contracts plus Flyway migrations 1 through 18. It then runs the
complete Phase 8 baseline, which supplies the full backend and frontend
regression gate.
