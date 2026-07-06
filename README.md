# Event-Driven Payments and Reconciliation Platform

A portfolio-quality simulation of payment processing, double-entry ledgering,
asynchronous event delivery and settlement reconciliation.

> **Educational system:** this application does not process real money and
> must not be used as a banking, payment-processing or accounting product.

## Project status

Current phase:

**Phase 6 — Frontend payment experience**

Phase 5 is complete and merged through PR #5. The platform now provides
authenticated synchronous internal GBP payments with durable idempotency,
atomic account-and-ledger posting, deterministic rejection and ownership-aware
payment lookup.

Phase 6 is now current. It will replace the foundation-only browser shell with
an authenticated customer workspace for session management, owned-account
views, exact GBP payment entry, retry-safe idempotent submission, payment
receipts and payment lookup.

No Phase 6 implementation has been accepted yet. Work begins with the frontend
architecture, browser-security and interaction decisions recorded in ADR 0010.

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

### Frontend

The frontend currently provides:

- a React and TypeScript application built with Vite;
- TanStack Query server-state management;
- a typed backend client with runtime response validation;
- loading, connected and unavailable backend states;
- manual connection retry behaviour;
- an accessible and responsive platform shell;
- Vitest component and API-client tests;
- Mock Service Worker network simulation; and
- ESLint static analysis.

### Delivery

The project currently provides:

- a PostgreSQL Docker Compose service;
- locked backend and frontend dependencies;
- reproducible PowerShell and Bash verification scripts; and
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
- reporting; and
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
the immutable double-entry ledger, synchronous payment submission and
ownership-aware payment lookup are also implemented. Frontend payment flows,
asynchronous event delivery, settlement, notification and reporting remain
planned work.

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
