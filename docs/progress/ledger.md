# Progress ledger

Last updated: 2026-06-29

## Status meanings

- **Completed:** acceptance criteria were executed and observed.
- **Current:** implementation is locally accepted but a remaining phase gate is
  still pending.
- **Not started:** no implementation has been accepted.
- **Blocked:** an external issue prevents progress.

## Environment preparation

| Requirement | Status | Evidence |
|---|---|---|
| WSL 2 | Completed | Ubuntu configured with WSL version 2 |
| Git | Completed | Git 2.51.2 |
| Java JDK | Completed | Java and javac 25.0.1 LTS |
| Node.js | Completed | Node.js 24.18.0 |
| npm | Completed | npm 10.8.3 |
| Corepack | Completed | Corepack 0.35.0 |
| pnpm | Completed | pnpm 11.9.0 |
| Docker Engine | Completed | Docker Engine 29.5.3 |
| Docker Compose | Completed | Docker Compose 5.1.4 |
| Docker execution | Completed | `hello-world` container succeeded |
| jq | Completed | jq 1.8.1 |
| IntelliJ repository | Completed | Repository and Gradle project configured |
| Current Git phase branch | Completed | `feat/phase-4-double-entry-ledger` |

## Phase progress

| Phase | Status | Evidence |
|---:|---|---|
| 0 — Architecture and repository foundation | Completed | Repository verifier passed; commits `0bab905` and `6cd81a5` |
| 1 — Backend, frontend and CI skeletons | Completed | Local and GitHub Actions verification passed; PR #1 ready to merge |
| 2 — Identity and access | Completed | PR #2 merged; local and GitHub Actions verification passed |
| 3 — Customers and accounts | Completed | PR #3 merged; local and GitHub Actions verification passed |
| 4 — Double-entry ledger | Current | Domain, persistence, database-invariant and query gates passed; final phase gate in progress |
| 5 — Synchronous payments | Not started | None |
| 6 — Frontend payment experience | Not started | None |
| 7 — Asynchronous events and outbox | Not started | None |
| 8 — Notifications and dead letters | Not started | None |
| 9 — Settlement and reconciliation | Not started | None |
| 10 — Audit and reporting | Not started | None |
| 11 — Observability and performance | Not started | None |
| 12 — Security hardening | Not started | None |
| 13 — Release infrastructure | Not started | None |
| 14 — Portfolio release | Not started | None |

## Phase 0 acceptance evidence

| Criterion | Status |
|---|---|
| Product scope is documented | Completed |
| System invariants are documented | Completed |
| C4 context diagram exists | Completed |
| C4 container diagram exists | Completed |
| Major initial decisions have ADRs | Completed |
| Backlog exists | Completed |
| Definition of done exists | Completed |
| Educational-use warning is prominent | Completed |
| Repository policy files exist | Completed |
| `scripts/verify-phase-0.sh` passes | Completed |
| `git diff --check` passes | Completed |
| Architecture foundation commit exists | Completed — `0bab905` |
| Phase completion commit exists | Completed — `6cd81a5` |

## Phase 1 acceptance evidence

### Backend and database

| Criterion | Status | Evidence |
|---|---|---|
| Spring Boot project builds | Completed | Gradle build passed |
| Java 25 toolchain is configured | Completed | Gradle toolchain verification |
| Gradle Wrapper 9.6.0 is committed | Completed | Wrapper files committed |
| Spring Modulith boundaries are declared | Completed | Module `package-info.java` files |
| Spring Modulith verification passes | Completed | `ModularityTest` |
| PostgreSQL integration works | Completed | Testcontainers integration test |
| Flyway migration pipeline works | Completed | Migration version 1 applied |
| Docker Compose PostgreSQL is healthy | Completed | PostgreSQL 18.4 health check |
| System-information endpoint works | Completed | `/api/v1/system/info` |
| Actuator health endpoints work | Completed | Health, liveness and readiness verified |
| OpenAPI output works | Completed | `/v3/api-docs` and Swagger UI verified |
| Correlation identifiers work | Completed | Supplied and generated identifiers verified |
| Executable JAR builds | Completed | `bootJar` passed |
| Backend foundation commit exists | Completed | `28cab8c` |

### Frontend

| Criterion | Status | Evidence |
|---|---|---|
| React and TypeScript application builds | Completed | Vite production build passed |
| TanStack Query is configured | Completed | Application query provider |
| Typed backend client exists | Completed | `getSystemInfo.ts` |
| Runtime API validation exists | Completed | Invalid contract test |
| Loading state exists | Completed | `SystemStatusPanel` |
| Connected state exists | Completed | Integrated browser verification |
| Backend error state exists | Completed | Component test |
| Manual retry works | Completed | User-event component test |
| API-client tests pass | Completed | 3 tests passed |
| Application tests pass | Completed | 3 tests passed |
| Frontend linting passes | Completed | ESLint passed |
| Frontend production build passes | Completed | Vite build passed |
| Backend/frontend integration works | Completed | Live system status rendered |
| Frontend foundation commit exists | Completed | `3a32741` |

### Continuous integration and verification

| Criterion | Status | Evidence |
|---|---|---|
| GitHub Actions workflow exists | Completed | `.github/workflows/ci.yml` |
| Repository checks are configured | Completed | Compose and Phase 0 checks |
| Backend CI job is configured | Completed | Java 25, tests and JAR packaging |
| Frontend CI job is configured | Completed | Install, lint, test and build |
| Linux Gradle executable bit is recorded | Completed | `backend/gradlew` mode `100755` |
| CI foundation commit exists | Completed | `7659347` |
| PowerShell Phase 1 verifier exists | Completed | `scripts/verify-phase-1.ps1` |
| Bash Phase 1 verifier exists | Completed | `scripts/verify-phase-1.sh` |
| PowerShell Phase 1 verifier passes | Completed | Observed on 2026-06-24 |
| Bash syntax validation passes | Completed | `bash -n` produced no errors |
| GitHub Actions run passes | Completed | Repository, Backend and Frontend checks passed on PR #1 |
| Main branch protection is configured | Completed | Pull requests and all three CI checks are required |
| Mobile-width browser check | Completed | Responsive layout verified at a 390px viewport |

## Verified Phase 1 results

The local Phase 1 verification gate completed successfully on 24 June 2026.

Observed results included:

- valid Docker Compose configuration;
- successful backend tests and executable JAR packaging;
- successful Spring Modulith architecture verification;
- successful Testcontainers PostgreSQL integration;
- successful Flyway migration verification;
- successful locked frontend dependency installation;
- successful ESLint analysis;
- 2 passing frontend test files;
- 6 passing frontend tests;
- successful TypeScript compilation;
- successful Vite production build; and
- no staged or unstaged whitespace errors.

The verifier ended with:

```text
Phase 1 verification passed.
```

## Phase 2 acceptance evidence

### Identity persistence and credentials

| Criterion | Status | Evidence |
|---|---|---|
| Identity users persist in PostgreSQL | Completed | `IdentityPersistenceIntegrationTest` |
| Email addresses are normalised | Completed | `EmailAddressTest` |
| Normalised emails are unique | Completed | Database constraint and registration tests |
| Password inputs are bounded | Completed | `PasswordPolicyTest` |
| Passwords are securely hashed | Completed | `PasswordHashingServiceTest` |
| Raw passwords are not persisted | Completed | Registration integration tests |

### Authentication and access control

| Criterion | Status | Evidence |
|---|---|---|
| Customer registration works | Completed | `CustomerRegistrationIntegrationTest` |
| Duplicate registration is rejected | Completed | Registration conflict tests |
| JDBC browser sessions work | Completed | `IdentitySessionIntegrationTest` |
| Login, current-session and logout APIs work | Completed | Session integration tests |
| CSRF protection is enabled | Completed | Security and session integration tests |
| Failed logins are tracked | Completed | Authentication-attempt tests |
| Accounts temporarily lock after repeated failures | Completed | Lockout tests |
| Session identifiers rotate during login | Completed | Session integration tests |
| Protected APIs reject anonymous callers | Completed | Authentication integration tests |
| Service-level method security works | Completed | `IdentityMethodSecurityIntegrationTest` |
| Administrators can manage roles | Completed | `IdentityRoleManagementIntegrationTest` |
| Non-administrators cannot manage roles | Completed | Negative authorisation tests |
| Role changes invalidate affected sessions | Completed | Role-management integration test |
| Final user role cannot be removed | Completed | Conflict integration test |

### Security audit and verification

| Criterion | Status | Evidence |
|---|---|---|
| Role grants create security events | Completed | `IdentitySecurityAuditIntegrationTest` |
| Role revocations create security events | Completed | `IdentitySecurityAuditIntegrationTest` |
| No-op role changes do not create events | Completed | Idempotency audit test |
| Security events cannot be updated | Completed | PostgreSQL trigger integration test |
| Security events cannot be deleted | Completed | PostgreSQL trigger integration test |
| Flyway migrations 2 through 4 apply cleanly | Completed | PostgreSQL integration suite |
| Phase 2 PowerShell verifier exists | Completed | `scripts/verify-phase-2.ps1` |
| Phase 2 Bash verifier exists | Completed | `scripts/verify-phase-2.sh` |
| Local Phase 2 verifier passes | Completed | PowerShell verifier passed on 2026-06-25 |
| GitHub Actions checks pass | Completed | Repository, Backend and Frontend checks passed on PR #2 |

## Phase 3 acceptance evidence

### Customer profiles and ownership

| Criterion | Status | Evidence |
|---|---|---|
| Customer profiles persist in PostgreSQL | Completed | `CustomerPersistenceIntegrationTest` |
| Customer names are bounded and reject control characters | Completed | `CustomerNameTest` |
| Customer lifecycle transitions are explicit | Completed | `CustomerProfileTest` |
| Operations and administrators manage customers | Completed | Customer management service and HTTP integration tests |
| Customer updates use optimistic concurrency | Completed | `CustomerConditionalUpdateHttpIntegrationTest` |
| Identity users can be assigned to customers | Completed | Customer ownership management integration tests |
| One identity cannot belong to multiple customers | Completed | Ownership conflict and database tests |
| Multiple identities can share one customer | Completed | Ownership HTTP integration test |
| Account creation requires an active customer | Completed | Customer eligibility integration tests |

### GBP accounts and customer views

| Criterion | Status | Evidence |
|---|---|---|
| GBP money uses integer minor units | Completed | `GbpAmountTest` |
| Accounts persist with GBP and zero starting balance | Completed | `CustomerAccountPersistenceIntegrationTest` |
| Negative balances are rejected | Completed | Domain and database integration tests |
| Account lifecycle transitions are explicit | Completed | `CustomerAccountTest` |
| Funded accounts cannot be closed | Completed | Account domain and HTTP integration tests |
| Operations and administrators manage accounts | Completed | Account management service and HTTP integration tests |
| Customer users see only owned accounts | Completed | Customer account query and ownership HTTP tests |
| Customer queries derive identity server-side | Completed | `CustomerAccountOwnershipHttpIntegrationTest` |
| Account updates use optimistic concurrency | Completed | `AccountConditionalUpdateHttpIntegrationTest` |
| Customer and account ordering is deterministic | Completed | Customer account query integration test |

### Validation, security and verification

| Criterion | Status | Evidence |
|---|---|---|
| Unknown JSON properties are rejected | Completed | Customer and account HTTP integration tests |
| Malformed UUID paths return stable problems | Completed | Customer and account HTTP integration tests |
| Missing conditional headers return `428` | Completed | Conditional update HTTP tests |
| Stale conditional writes return `412` | Completed | Conditional update HTTP tests |
| Security failures use problem responses | Completed | Security foundation and account HTTP tests |
| Security responses use `Cache-Control: no-store` | Completed | Security integration tests |
| Flyway migrations 5 through 8 apply cleanly | Completed | Customer and account persistence tests |
| Spring Modulith verification passes | Completed | `ModularityTest` |
| Full backend test and JAR gate passes | Completed | `clean test bootJar` on 2026-06-29 |
| Complete Phase 2 baseline verifier passes | Completed | PowerShell verifier on 2026-06-29 |

## Phase 4 acceptance evidence

### Ledger domain and posting

| Criterion | Status | Evidence |
|---|---|---|
| GBP ledger values use exact integer minor units | Completed | `GbpAmount` and ledger domain tests |
| Entries use explicit debit and credit sides | Completed | `LedgerSide` and `LedgerEntrySide` |
| Transactions require two or more entries | Completed | Domain and PostgreSQL invariant tests |
| Transactions require debit and credit sides | Completed | Domain and PostgreSQL invariant tests |
| Debit and credit totals must balance exactly | Completed | Domain factory and deferred constraint trigger |
| Arithmetic overflow is rejected | Completed | `LedgerTransactionTest` |
| Posting stores one header and all entries atomically | Completed | `LedgerPostingServiceIntegrationTest` |
| Persistence failures roll back the complete posting | Completed | Missing-account rollback integration test |
| Corrections link to the original transaction | Completed | Persistence integration test |

### PostgreSQL integrity and immutability

| Criterion | Status | Evidence |
|---|---|---|
| Flyway migration 9 creates ledger tables | Completed | `LedgerPersistenceIntegrationTest` |
| Flyway migration 10 creates invariant triggers | Completed | `LedgerDatabaseInvariantIntegrationTest` |
| Deferred checks run at transaction commit | Completed | Header-only, one-sided and unbalanced commit tests |
| Posted headers reject updates | Completed | Database immutability integration test |
| Posted headers reject deletes | Completed | Database immutability integration test |
| Posted entries reject updates | Completed | Database immutability integration test |
| Posted entries reject deletes | Completed | Database immutability integration test |
| Database foreign keys protect referenced accounts | Completed | Persistence integration test |
| Entry amount, currency, side and sequence are constrained | Completed | Persistence integration tests |

### Queries, verification and architecture

| Criterion | Status | Evidence |
|---|---|---|
| Transactions reload with ordered entries | Completed | `LedgerQueryServiceIntegrationTest` |
| Account history ordering is deterministic | Completed | `LedgerQueryServiceIntegrationTest` |
| Empty-account verification returns zero totals | Completed | `LedgerQueryServiceIntegrationTest` |
| Snapshot and ledger totals can be compared | Completed | Consistent and inconsistent verification tests |
| Missing transactions and accounts are explicit | Completed | Query-service integration tests |
| Ledger module exposes a public posting API | Completed | `LedgerPostingService` |
| Ledger module exposes public query APIs | Completed | `LedgerQueryService` |
| Ledger depends only on shared | Completed | `ModularityTest` |
| Focused Phase 4 ledger suite passes | Completed | Local Gradle verification on 2026-06-29 |

## Decision history

| Date | Decision |
|---|---|
| 2026-06-24 | Start with a Spring Modulith modular monolith |
| 2026-06-24 | Use Java 25 and Spring Boot 4.0.7 |
| 2026-06-24 | Use Gradle Wrapper 9.6.0 |
| 2026-06-24 | Use exact decimal money and a double-entry ledger |
| 2026-06-24 | Use PostgreSQL-backed browser sessions |
| 2026-06-24 | Use an explicit transactional outbox |
| 2026-06-24 | Require scoped idempotency keys for payment submission |
| 2026-06-24 | Use PostgreSQL 18 with its version-aware Docker volume layout |
| 2026-06-24 | Use React, TypeScript, Vite and TanStack Query |
| 2026-06-24 | Use Vitest, Testing Library and MSW for frontend testing |
| 2026-06-24 | Use GitHub Actions for repository, backend and frontend verification |
| 2026-06-25 | Use BCrypt password hashing through Spring Security |
| 2026-06-25 | Use PostgreSQL-backed server-side browser sessions |
| 2026-06-25 | Lock accounts temporarily after repeated failed login attempts |
| 2026-06-25 | Enforce role management at the service boundary |
| 2026-06-25 | Store immutable role-change security audit events |
| 2026-06-26 | Represent GBP account values in integer minor units |
| 2026-06-26 | Keep identity-to-customer ownership in the customer module |
| 2026-06-29 | Require strong ETags and `If-Match` for mutable Phase 3 resources |
| 2026-06-29 | Reject unknown JSON properties and malformed resource identifiers |
| 2026-06-29 | Standardise authentication and authorisation failures as problem responses |
| 2026-06-29 | Use non-negative GBP minor units with explicit debit and credit sides |
| 2026-06-29 | Enforce balanced journals in the domain and with deferred PostgreSQL checks |
| 2026-06-29 | Make posted ledger headers and entries append-only |
| 2026-06-29 | Use compensating transactions instead of mutating financial history |
| 2026-06-29 | Keep ledger posting and query APIs independent of account internals |

## Next verified action

Add and execute the dedicated Phase 4 PowerShell and Bash verification scripts,
commit the final Phase 4 evidence and open the double-entry-ledger pull request.

After the required repository, backend and frontend checks pass:

1. review the complete Phase 4 diff;
2. merge the pull request into `main`;
3. synchronise the local `main` branch;
4. remove the completed Phase 4 branch; and
5. create the Phase 5 synchronous-payments branch.
