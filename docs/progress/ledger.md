# Progress ledger

Last updated: 2026-06-24

## Status meanings

- **Completed:** acceptance criteria were executed and observed.
- **Current:** work or verification is in progress.
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
| IntelliJ repository | Completed | Empty project created |
| Git phase branch | Completed | `chore/phase-0-foundation` |

## Phase progress

| Phase | Status | Evidence |
|---:|---|---|
| 0 — Architecture and repository foundation | Current | Files created; content and verification pending |
| 1 — Backend, frontend and CI skeletons | Not started | None |
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
| Product scope is documented | Prepared |
| System invariants are documented | Prepared |
| C4 context diagram exists | Prepared |
| C4 container diagram exists | Prepared |
| Major initial decisions have ADRs | Prepared |
| Backlog exists | Prepared |
| Definition of done exists | Prepared |
| Educational-use warning is prominent | Prepared |
| Repository policy files exist | Prepared |
| `scripts/verify-phase-0.sh` passes | Awaiting execution |
| `git diff --check` passes | Awaiting execution |
| Phase 0 commit exists | Awaiting completion |

Phase 0 becomes Completed only after the verifier output and Git checks have
been observed.

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

## Next verified action

Populate all Phase 0 files, run the Phase 0 verifier and inspect its actual
output before starting Phase 1.
