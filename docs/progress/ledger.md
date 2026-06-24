# Progress ledger

Last updated: 2026-06-24

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
| Current Git phase branch | Completed | `chore/phase-1-project-skeleton` |

## Phase progress

| Phase | Status | Evidence |
|---:|---|---|
| 0 — Architecture and repository foundation | Completed | Repository verifier passed; commits `0bab905` and `6cd81a5` |
| 1 — Backend, frontend and CI skeletons | Current | Local verifier passed; GitHub Actions observation remains |
| 2 — Identity and access | Not started | None |
| 3 — Customers and accounts | Not started | None |
| 4 — Double-entry ledger | Not started | None |
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
| GitHub Actions run passes | Pending | Branch has not yet been pushed |
| Mobile-width browser check | Pending | Explicit 390px check not yet recorded |

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

## Next verified action

Complete the explicit mobile-width browser check, push the Phase 1 branch and
observe the GitHub Actions workflow.

After both gates pass:

1. mark Phase 1 as completed;
2. commit the final progress-ledger update;
3. merge `chore/phase-1-project-skeleton` into `main`; and
4. create the Phase 2 identity-and-access branch.
