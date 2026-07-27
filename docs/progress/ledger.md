# Progress ledger

Last updated: 2026-07-27

## Status meanings

- **Completed:** acceptance criteria were executed and observed.
- **Current:** the active phase has implementation or a remaining gate in
  progress.
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
| Current Git phase branch | Current | `feat/phase-13-release-infrastructure` from the merged Phase 12 `main` baseline |

## Phase progress

| Phase | Status | Evidence |
|---:|---|---|
| 0 — Architecture and repository foundation | Completed | Repository verifier passed; commits `0bab905` and `6cd81a5` |
| 1 — Backend, frontend and CI skeletons | Completed | Local and GitHub Actions verification passed; PR #1 ready to merge |
| 2 — Identity and access | Completed | PR #2 merged; local and GitHub Actions verification passed |
| 3 — Customers and accounts | Completed | PR #3 merged; local and GitHub Actions verification passed |
| 4 — Double-entry ledger | Completed | PR #4 merged; local and GitHub Actions verification passed |
| 5 — Synchronous payments | Completed | PR #5 merged; local and GitHub Actions verification passed |
| 6 — Frontend payment experience | Completed | PR #6 merged; local and GitHub Actions verification passed |
| 7 — Asynchronous events and outbox | Completed | PR #7 merged; local and GitHub Actions verification passed |
| 8 — Notifications and dead letters | Completed | PR #8 merged at `179d793`; local and remote Phase 8 feature branches removed |
| 9 — Settlement and reconciliation | Completed | PR #9 merged at `43b697e`; local and remote Phase 9 branches removed |
| 10 — Audit and reporting | Completed | PR #10 verification recorded on `main` |
| 11 — Observability and performance | Completed | Implementation, controlled verification and documentation merged into `main` on 2026-07-26 |
| 12 — Security hardening | Completed | PR #12 merged into `main`; local verification and all five GitHub Actions checks passed |
| 13 — Release infrastructure | Current | Release-foundation implementation batch in progress |
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

## Phase 5 acceptance evidence

### Domain, persistence and idempotency

| Criterion | Status | Evidence |
|---|---|---|
| Payment request data uses positive GBP minor units | Completed | `PaymentRequestDataTest` |
| Payment lifecycle permits only accepted transitions | Completed | `PaymentTest` |
| Terminal rejection and failure reasons use stable codes | Completed | Payment domain and response-factory tests |
| Idempotency keys are bounded visible ASCII values | Completed | `IdempotencyKeyTest` |
| Request fingerprints are canonical versioned SHA-256 values | Completed | `PaymentRequestFingerprintTest` |
| Flyway migration 11 creates payment and idempotency persistence | Completed | `PaymentPersistenceIntegrationTest` |
| Flyway migration 12 permits unknown rejected account references | Completed | Unknown-account rejection integration tests |
| Database constraints protect payment and idempotency invariants | Completed | `PaymentPersistenceIntegrationTest` |
| Terminal responses are bounded and retained for 24 hours | Completed | `StoredPaymentResponseTest` and idempotency-record tests |
| New, replay, conflict and stale-lease reservation paths work | Completed | `PaymentReservationCoordinatorIntegrationTest` |

### Account mutation and atomic processing

| Criterion | Status | Evidence |
|---|---|---|
| Customer ownership has a non-throwing public lookup | Completed | Customer ownership integration tests |
| Account mutation stays behind the public account module API | Completed | `AccountPaymentMutationServiceIntegrationTest` |
| Source ownership, account state, currency and funds are checked before mutation | Completed | Account payment service unit and integration tests |
| Expected business refusals return typed rejection results | Completed | `AccountPaymentResultTest` |
| Approved payments update both snapshots and one balanced ledger atomically | Completed | `PaymentProcessingIntegrationTest` |
| Rejected payments change no account or ledger state | Completed | `PaymentProcessingIntegrationTest` |
| Processing failures roll back and finalise a bounded failure response | Completed | Processing coordinator and integration tests |
| Concurrency conflicts retry the complete transaction at most three times | Completed | `PaymentProcessingCoordinatorTest` |
| Spring Modulith boundaries remain valid | Completed | `ModularityTest` |

### HTTP, security and verification

| Criterion | Status | Evidence |
|---|---|---|
| `POST /api/v1/payments` submits and exactly replays payments | Completed | `PaymentSubmissionHttpIntegrationTest` |
| Invalid input and idempotency headers create no reservation | Completed | Submission HTTP integration tests |
| Submission requires authenticated `CUSTOMER` authority and CSRF | Completed | Submission HTTP integration tests |
| `GET /api/v1/payments/{paymentId}` returns a safe payment projection | Completed | `PaymentQueryHttpIntegrationTest` |
| Customers read only their own submitted payments | Completed | Query service and HTTP ownership tests |
| Foreign and missing payments are indistinguishable to customers | Completed | `PaymentQueryHttpIntegrationTest` |
| `OPERATIONS` and `ADMIN` read any payment | Completed | Payment query HTTP integration tests |
| `RECONCILIATION_ANALYST` has no Phase 5 payment-read access | Completed | Payment query HTTP integration test |
| Payment problems and OpenAPI contracts are stable | Completed | Submission and query HTTP integration tests |
| Focused payment HTTP and service suite passes | Completed | 5 suites and 30 tests on 2026-07-03 |
| Complete backend regression passes | Completed | 59 suites and 366 tests on 2026-07-03 |
| PowerShell and Bash Phase 5 verifiers exist | Completed | `scripts/verify-phase-5.ps1` and `.sh` |
| Composite Phase 5 verifier passes | Completed | PowerShell verifier passed on 2026-07-03 |
| Required GitHub Actions checks pass | Completed | Repository, Backend and Frontend checks passed on PR #5 |

## Phase 6 acceptance evidence

| Criterion | Status | Evidence |
|---|---|---|
| Frontend payment boundaries and browser-security decisions are documented | Completed | ADR 0010 |
| Shared authenticated API and problem handling exists | Completed | `apiClient.ts`, `apiProblem.ts` and API-client tests |
| Session bootstrap, login and logout work | Completed | `SessionBoundary` and identity-session workflow tests |
| Customer-owned accounts and exact GBP balances are displayed | Completed | `CustomerAccountsPanel` component tests |
| GBP input converts directly to integer minor units | Completed | GBP model and `PaymentAmountInput` tests |
| One logical payment keeps one idempotency key across retries | Completed | Submission API and payment-envelope tests |
| Successful, rejected, failed and in-progress outcomes are accessible | Completed | Payment form and receipt workflow tests |
| Customer-owned payment lookup works | Completed | `PaymentLookup` API and component tests |
| Frontend lint, tests and production build pass | Completed | ESLint, 23 test files and 127 tests, and Vite build on 2026-07-06 |
| PowerShell and Bash Phase 6 verifiers exist | Completed | `scripts/verify-phase-6.ps1` and `.sh` |
| Composite Phase 6 verifier passes | Completed | PowerShell verifier passed on 2026-07-06 |
| Required GitHub Actions checks pass | Completed | Repository, Backend and Frontend checks passed on PR #6 |

## Phase 7 acceptance evidence

| Criterion | Status | Evidence |
|---|---|---|
| Asynchronous event and outbox boundaries are documented | Completed | ADR 0011 |
| Outbox schema persists event, payload and delivery metadata | Completed | Flyway migration V13 and PostgreSQL integration tests |
| Completed payments create one outbox event atomically | Completed | `PaymentProcessingIntegrationTest` |
| Rejected and failed payments do not create completed-payment events | Completed | `PaymentProcessingIntegrationTest` |
| Event payloads use stable non-sensitive `payment.completed.v1` JSON | Completed | `PaymentCompletedOutboxEventFactoryTest` |
| Bounded event claiming uses owner tokens and leases | Completed | Outbox domain and publication integration tests |
| Successful simulated publication marks events as published | Completed | `OutboxPublicationIntegrationTest` |
| Retryable publication failure increments attempts and schedules retry | Completed | `OutboxPublicationIntegrationTest` |
| Exhausted publication failure moves events to dead letter | Completed | `OutboxEventTest` exhaustion coverage |
| Spring Modulith boundaries remain valid | Completed | `ModularityTest` passed in the composite verifier |
| Focused Phase 7 backend suite passes | Completed | Outbox, payment-event and modularity suite passed on 2026-07-23 |
| PowerShell and Bash Phase 7 verifiers exist | Completed | `scripts/verify-phase-7.ps1` and `.sh` |
| Composite Phase 7 verifier passes | Completed | PowerShell verifier passed on 2026-07-23 |
| Required GitHub Actions checks pass | Completed | Repository, Backend and Frontend checks passed on PR #7 |
## Phase 8 acceptance evidence

| Criterion | Status | Evidence |
|---|---|---|
| Notification and dead-letter boundaries are documented | Completed | ADR 0012 and architecture overview |
| Published outbox events are exposed through a narrow public read API | Completed | `PublishedOutboxEventReader` and stable-page integration coverage |
| Notification consumer checkpoint is durable | Completed | Flyway migration V14 and `NotificationEventConsumerIntegrationTest` |
| Notification creation is idempotent by source event identifier | Completed | Unique database constraint and duplicate-replay integration test |
| `payment.completed.v1` creates one structured notification | Completed | Strict payload mapper and PostgreSQL consumer integration test |
| Invalid supported payloads become inspectable consumer failures | Completed | Durable consumer-failure persistence and progress test |
| Notification delivery uses bounded owner-token leases | Completed | `NotificationClaimingService` and delivery integration tests |
| Retryable notification failures schedule bounded retries | Completed | Delivery retry and deterministic-jitter integration coverage |
| Permanent or exhausted notification failures enter dead letter | Completed | Permanent and exhausted delivery integration tests |
| Customers can read only their own simulated notifications | Completed | Method-security, MockMvc and React notification tests |
| Administrators can inspect outbox dead-letter events | Completed | Admin service, HTTP integration and recovery-interface tests |
| Administrators can replay only eligible dead-letter events | Completed | Dead-letter state, role, CSRF and optimistic-version checks |
| Replay preserves the immutable event contract and records audit evidence | Completed | Flyway migration V15, replay service and immutable audit trigger |
| Replayed source events do not duplicate notification side effects | Completed | Unique source-event deduplication and repeated-consumption coverage |
| Spring Modulith boundaries remain valid | Completed | `ModularityTest` passed in focused Phase 8 suites |
| Focused Phase 8 backend and frontend suites pass | Completed | Backend notification suites, 16 focused frontend tests, lint and build passed on 2026-07-23 |
| PowerShell and Bash Phase 8 verifiers exist | Completed | `scripts/verify-phase-8.ps1` and `scripts/verify-phase-8.sh` |
| Composite Phase 8 verifier passes | Completed | PowerShell verifier passed on 2026-07-23 |
| Required GitHub Actions checks pass | Completed | Repository, Backend and Frontend checks passed on PR #8 at `5031c66` on 2026-07-23 |
## Phase 9 acceptance evidence

| Criterion | Status | Evidence |
|---|---|---|
| Phase 8 merge and branch cleanup recorded | Completed | PR #8 merge commit `179d793` and Phase 9 baseline documentation |
| ADR 0013 exists | Completed | `docs/adr/0013-settlement-and-reconciliation.md` |
| Settlement and reconciliation boundaries are documented | Completed | ADR 0013, README and architecture overview |
| Exact bounded CSV contract is implemented | Completed | `SettlementCsvParser` and parser tests |
| Complete file is validated before persistence | Completed | Parser-first import service and invalid-file rollback coverage |
| Duplicate accepted uploads are idempotent by SHA-256 fingerprint | Completed | V16 unique fingerprint and import workflow replay tests |
| Imported settlement rows are immutable | Completed | V16 mutation triggers and PostgreSQL integration tests |
| External settlement record identifiers are globally unique | Completed | V16 unique constraint and conflicting-import rollback test |
| Payment data is accessed only through a public reconciliation reader | Completed | `PaymentReconciliationReader` module boundary |
| Payment snapshots are fetched in one bounded batch | Completed | `PaymentReconciliationReaderService` and focused unit test |
| Deterministic matching and discrepancy codes are implemented | Completed | `SettlementMatcher` precedence tests |
| One accepted settlement match per payment is database protected | Completed | V17 match claims and concurrency integration coverage |
| Import, rows, results and discrepancies commit atomically | Completed | Import workflow and PostgreSQL rollback tests |
| Reconciliation never mutates payment, account, ledger or outbox history | Completed | Read-only payment boundary and persistence integration tests |
| Analyst and administrator security is enforced | Completed | Method-security and authenticated MockMvc tests |
| Import and discrepancy APIs use no-store responses | Completed | HTTP integration tests |
| Deterministic bounded keyset pagination exists | Completed | Result and discrepancy query services plus HTTP tests |
| Discrepancy resolution uses strong ETags and `If-Match` | Completed | Version precondition and stale-write HTTP tests |
| Immutable attributable resolution evidence exists | Completed | V18 triggers and resolution persistence tests |
| Spring Modulith verification passes | Completed | `ModularityTest` passed in focused Phase 9 suites |
| PostgreSQL integration tests pass | Completed | Import, reconciliation and resolution integration suites |
| Authenticated MockMvc tests pass | Completed | Import and discrepancy HTTP integration suites |
| React analyst workflow passes frontend tests | Completed | 22 focused tests, ESLint and production build passed on 2026-07-24 |
| Phase 9 PowerShell and Bash verifiers exist | Completed | `scripts/verify-phase-9.ps1` and `scripts/verify-phase-9.sh` |
| Cumulative Phase 9 verifier passes | Completed | PowerShell verifier passed on 2026-07-24 |
| GitHub Repository, Backend and Frontend checks pass | Completed | Final PR #9 head `c6f95f3` passed all three checks before merge `43b697e` on 2026-07-24 |

## Phase 10 acceptance criteria

### Audit ownership and persistence

| Criterion | Status | Evidence |
|---|---|---|
| Phase 9 merge and branch cleanup are recorded | Completed | PR #9 merge `43b697e` and Phase 10 baseline documentation |
| ADR 0014 defines audit and reporting boundaries | Completed | `docs/adr/0014-audit-and-operational-reporting.md` |
| Canonical business-audit events are append-only | Completed | V19 mutation trigger and `BusinessAuditPersistenceIntegrationTest` |
| Canonical event metadata is bounded, versioned and allow-listed | Completed | Audit request, serializer, event-type validator and PostgreSQL constraints |
| Source-event recording is idempotent | Completed | Unique source key plus same-event replay and conflicting-event tests |
| Audit writes commit atomically with their business mutation | Completed | Payment, customer, account and settlement transaction integration tests |
| Existing role-change, replay and resolution evidence remains source-owned | Completed | Identity, outbox and reconciliation public evidence readers |
| No misleading historical canonical backfill is created | Completed | V19 creates an empty canonical journal; normalized search composes source evidence at read time |

### Search, authorization and reporting

| Criterion | Status | Evidence |
|---|---|---|
| Audit search uses deterministic bounded keyset pagination | Completed | `AuditCursorCodecTest`, merge tests and PostgreSQL HTTP pagination coverage |
| Audit filters are allow-listed and use UTC half-open time windows | Completed | `AuditSearchFilterTest` and HTTP boundary coverage |
| Role visibility is enforced before paging and aggregation | Completed | Authority-scoped readers and cross-role HTTP tests |
| Customers have no Phase 10 audit or report access | Completed | Authenticated audit, summary and export authorization tests |
| Summaries and exports use read-only repeatable-read snapshots | Completed | `OperationalSummaryService`, `ReportExportService` and PostgreSQL integration tests |
| Payment, settlement and reconciliation summaries are exact | Completed | Fixture-based aggregate assertions across all three report sections |
| Summary and export queries cannot mutate source records | Completed | Public read-only module boundaries and read-only reporting transactions |
| Audit and reporting responses use `Cache-Control: no-store` | Completed | Audit and operational reporting HTTP integration tests |

### CSV export and frontend

| Criterion | Status | Evidence |
|---|---|---|
| Export windows are required and capped at 31 days | Completed | `ReportWindowTest` and authenticated HTTP validation |
| Every export is capped at 10,000 data rows | Completed | Independently bounded readers and overflow HTTP coverage |
| CSV uses fixed typed columns and RFC 4180 records | Completed | `CsvDocumentWriterTest` and exact export byte assertions |
| Free-text and formula-capable values are excluded | Completed | Typed export mappings and sensitive/formula fixture assertions |
| Download filenames and response headers are safe | Completed | Fixed filenames, `nosniff`, no-store and content-disposition HTTP assertions |
| Role-gated audit and reporting workspace is implemented | Completed | `AuditReportingWorkspace.test.tsx` and workspace role tests |
| Session expiry clears audit and reporting query state | Completed | Reporting cache-isolation tests |
| Frontend lint, tests and production build pass | Completed | ESLint, 160 Vitest tests and Vite build passed on 2026-07-25 |
| Spring Modulith verification passes | Completed | `ModularityTest` passed in focused Phase 10 suites |
| PowerShell and Bash Phase 10 verifiers exist | Completed | `scripts/verify-phase-10.ps1` and `scripts/verify-phase-10.sh` |
| Cumulative Phase 10 verifier passes | Completed | PowerShell verifier passed on 2026-07-26 |
| GitHub Repository, Backend and Frontend checks pass | Completed | PR #10 head `c494a67` passed all three checks on 2026-07-26 |

## Phase 11 acceptance criteria

### Foundation observability

| Criterion | Status | Evidence |
|---|---|---|
| Phase 10 merge and Phase 11 branch baseline are recorded | Completed | Phase 10 merge `1791b9f` and `feat/phase-11-observability-performance` |
| ADR 0015 defines observability and performance boundaries | Completed | `docs/adr/0015-observability-and-performance.md` |
| Correlation-aware request completion events are structured and allow-listed | Completed | `RequestCompletionLoggingFilter` and ECS logging configuration |
| Prometheus-compatible Actuator metrics are exposed | Completed | Micrometer Prometheus registry and protected Actuator endpoints |
| Payment lifecycle metrics are recorded without high-cardinality labels | Completed | `PaymentMetrics` and `PaymentMetricsTest` |
| Readiness includes PostgreSQL health while liveness remains process health | Completed | `application.yml` health groups |
| Focused Phase 11 foundation tests pass | Completed | `PaymentMetricsTest`, `PaymentSubmissionServiceTest` and `PaymentProcessingCoordinatorTest` passed on 2026-07-26 |

### Remaining Phase 11 work

| Criterion | Status | Evidence |
|---|---|---|
| Distributed trace propagation and export are implemented | Completed | Batch 2 adds Micrometer/OpenTelemetry observations, configurable OTLP export and payment processing spans; focused tests passed on 2026-07-26 |
| Controlled failure simulation is profile-gated and audited | Completed | Batch 2 adds the bounded administrator-only in-memory simulator; focused tests passed on 2026-07-26 |
| Reproducible payment-path load tests are implemented | Completed | `load-tests/payment-submission.js`, runbook and PowerShell/Bash static verifiers |
| Performance methodology, measurements and SLO evidence are recorded | Completed | Phase 11 was closed with controlled verification; no production capacity or SLO claim is made |

## Phase 12 acceptance criteria

| Criterion | Status | Evidence |
|---|---|---|
| Phase 12 scope and security boundaries are recorded | Completed | ADR 0016 |
| Threat model covers replay, access control, injection, sensitive data and denial of service | Completed | `docs/security/threat-model.md` |
| API security headers are explicit and HTTPS-aware | Completed | `SecurityHeadersFilter` and regression tests |
| Sensitive write routes have bounded abuse protection | Completed | Fixed-window limiter, bounded state and rate-limit tests |
| Settlement multipart and parser limits are enforced | Completed | 1 MiB web cap, parser cap and existing upload tests |
| Sensitive values are excluded from request-completion logs | Completed | Allow-listed logging and query-string regression test |
| Security dependency and static-analysis checks are present | Completed | CI security job and Phase 12 static verifier |
| Focused and cumulative Phase 12 verification passes | Completed | Local Phase 12 verification passed; PR #12 passed all five GitHub Actions checks on 2026-07-26 |

## Phase 13 release-foundation acceptance criteria

| Criterion | Status | Evidence |
|---|---|---|
| Release composition is separate from PostgreSQL-only development Compose | Completed | `compose.release.yaml` and ADR 0017 |
| PostgreSQL health gates backend startup | Completed | Compose health check and `depends_on` condition |
| Backend image uses deterministic executable JAR packaging | Completed | `backend/build.gradle.kts` and `backend/Dockerfile` |
| Backend and frontend containers run without root privileges | Completed | Container definitions and static verifier |
| Browser and API share one Nginx origin | Completed | `frontend/nginx.conf` |
| Release static contract and local smoke gate exist | Completed | `scripts/verify-phase-13.ps1` |
| Docker image build and runtime smoke test pass locally | Completed | Local Docker image build and runtime smoke verification passed on 2026-07-27 via `scripts/verify-phase-13.ps1` |
| Required GitHub Actions checks pass | Completed | Repository, Backend and Frontend checks passed on the Phase 13 pull request |

## Phase 14 portfolio-release acceptance criteria

| Criterion | Status | Evidence |
|---|---|---|
| Phase 14 scope and educational boundaries are recorded | Completed | README and portfolio project overview |
| Recruiter-facing project overview exists | Completed | `docs/portfolio/README.md` |
| Architecture and lifecycle diagrams exist | Completed | `docs/architecture/diagrams.md` |
| Five-minute interview/demo runbook exists | Completed | `docs/portfolio/demo-runbook.md` |
| Payment-platform README links the portfolio evidence | Completed | README Portfolio and interview materials section |
| Resume site presents the platform as a portfolio project | Completed | `SamHarrison1999/resume` Projects and Portfolio sections |
| Local documentation and site validation pass | Current | Run repository checks and inspect the rendered portfolio page |
| Required GitHub Actions checks pass | Completed | PR #14 head `b5ffbbd` passed Repository, Backend and Frontend checks on 2026-07-27 |
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
| 2026-06-29 | Reserve idempotency and a pending payment before financial posting |
| 2026-06-29 | Recover stale in-progress requests through a bounded processing lease |
| 2026-06-29 | Keep account balance mutation behind a public account-module API |
| 2026-06-29 | Return typed account-payment rejections instead of throwing expected business outcomes |
| 2026-06-29 | Resolve the current actor through `CurrentIdentityUser` and use the repository's `CUSTOMER`, `OPERATIONS` and `ADMIN` roles |
| 2026-06-29 | Retry the complete payment transaction at most three times after concurrency conflicts |
| 2026-06-29 | Extend the posting transaction with outbox and audit records only in their later phases |
| 2026-07-03 | Persist exact bounded terminal idempotency responses for 24 hours using explicit `PROCESSING` and `COMPLETED` record states |
| 2026-07-06 | Keep browser credentials and CSRF tokens out of persistent browser storage |
| 2026-07-06 | Validate API responses at runtime and use TanStack Query for server state |
| 2026-07-06 | Convert GBP text directly to integer minor units without floating-point arithmetic |
| 2026-07-06 | Bind one idempotency key to one exact payment draft and reuse it for retries |
| 2026-07-06 | Retain at most one unresolved idempotency envelope in bounded session storage |
| 2026-07-06 | Keep Phase 6 in one accessible workspace without adding a routing dependency |
| 2026-07-06 | Establish the Phase 7 outbox before adding broker infrastructure |
| 2026-07-06 | Publish only successful `payment.completed.v1` events in the first asynchronous boundary |
| 2026-07-06 | Use owner-token leases for bounded outbox publication claims |
| 2026-07-23 | Deduplicate notifications with a unique source outbox event identifier |
| 2026-07-23 | Keep notification delivery retries independent of payment and outbox publication |
| 2026-07-23 | Restrict outbox dead-letter inspection and controlled replay to administrators |
| 2026-07-23 | Preserve event payloads during replay and record immutable replay evidence |
| 2026-07-24 | Parse and validate the complete bounded settlement file before persistence |
| 2026-07-24 | Identify accepted imports idempotently by SHA-256 of the original raw bytes |
| 2026-07-24 | Read reconciliation-safe payment snapshots through one bounded public batch API |
| 2026-07-24 | Keep reconciliation state and resolution evidence separate from payment and ledger history |
| 2026-07-24 | Use database-protected accepted-match claims and immutable per-row results |
| 2026-07-24 | Require strong ETags and `If-Match` for one-time discrepancy resolution |
| 2026-07-24 | Preserve source-owned evidence and do not invent a canonical historical backfill |
| 2026-07-24 | Record new business-audit events atomically in an append-only canonical journal |
| 2026-07-24 | Apply role visibility before audit pagination, aggregation and export |
| 2026-07-24 | Bound synchronous CSV exports to 31 days and 10,000 typed rows |
| 2026-07-26 | Use ECS structured logs, low-cardinality Micrometer metrics and database-aware readiness |
| 2026-07-26 | Use Micrometer Observation with OpenTelemetry/OTLP for optional trace export |
| 2026-07-26 | Keep failure simulation disabled by default, bounded, in-memory and administrator-only |
| 2026-07-26 | Measure the authenticated payment path with a security-preserving k6 harness and external fixture inputs |
| 2026-07-26 | Keep Phase 12 rate limiting bounded and single-process until deployment infrastructure justifies a shared store |
| 2026-07-26 | Apply strict API response headers without breaking the local Swagger development surface |
| 2026-07-27 | Keep release-shaped Compose separate from the PostgreSQL-only development workflow |
| 2026-07-27 | Serve the browser and API through one unprivileged Nginx origin |

## Next verified action

Commit and push this CI-evidence documentation update, then rerun the
Repository, Backend and Frontend checks on the updated Phase 14 payment-platform
pull request before merging the two Phase 14 pull requests.