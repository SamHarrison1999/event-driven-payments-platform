# Portfolio project overview

## One-line summary

The Event-Driven Payments and Reconciliation Platform is a full-stack educational simulation of an internal payment workflow, built to demonstrate reliable state transitions, financial invariants, asynchronous delivery and operational investigation.

It is a portfolio project. It does not process real money and must not be used as a banking, payment-processing or accounting product.

## The engineering problem

A payment workflow is more than an HTTP endpoint that moves a balance. A credible implementation must address:

- duplicate requests and client retries;
- balanced financial postings;
- concurrent updates;
- durable work that must happen after a transaction commits;
- duplicate or failed event delivery;
- role-scoped access to operational data;
- settlement files that disagree with internal records; and
- traceable evidence for investigation.

The project models those concerns in one modular monolith so that transaction boundaries and module ownership remain visible.

## What the system demonstrates

| Area | Demonstrated design |
|---|---|
| Financial integrity | Immutable double-entry journals, positive GBP minor units, balanced debit and credit totals, append-only posted history |
| Safe retries | Scoped idempotency keys, request fingerprints, bounded terminal-response replay and stale-processing recovery |
| Asynchronous work | Transactional outbox, owner-token publication leases, retry scheduling and dead-letter handling |
| Consumer reliability | Durable checkpoints, unique source-event deduplication, notification retries and controlled dead-letter replay |
| Reconciliation | Strict bounded CSV imports, raw-byte idempotency, immutable row results, discrepancy resolution and attributable evidence |
| Security | Session-based authentication, CSRF protection, role-based access control, ownership checks, rate limits and security headers |
| Operations | Structured request completion logs, low-cardinality metrics, optional OTLP tracing, audit search, summaries and typed CSV exports |
| Delivery | PostgreSQL-backed local development, Docker Compose release packaging, unprivileged containers and GitHub Actions verification |

## Architecture in brief

The browser uses a React and TypeScript frontend. In the release composition, Nginx serves the frontend and proxies `/api/` requests to the Spring Boot backend through one origin. The backend is a Java 25 and Spring Boot 4 modular monolith using Spring Modulith boundaries. PostgreSQL is the system of record, and Flyway owns forward-only schema migrations.

The core business path is:

1. authenticate the caller and apply role and ownership rules;
2. reserve or replay the caller's idempotency key;
3. validate and post a balanced double-entry journal;
4. update the payment and account state atomically;
5. record a successful-payment outbox event in the same transaction; and
6. publish and consume the event with leases, retries and deduplication.

Settlement reconciliation is deliberately separate from payment processing. It reads bounded payment snapshots through a public reconciliation reader and records import, result, discrepancy and resolution evidence without mutating payment, account, ledger or outbox history.

See the [architecture diagrams](../architecture/diagrams.md) for the main flows and state transitions.

## Portfolio evidence

| Evidence | Link |
|---|---|
| Full implementation and setup instructions | [Repository README](../../README.md) |
| Architecture decisions | [ADR directory](../adr/) |
| Security boundaries and threat model | [Threat model](../security/threat-model.md) |
| Verified implementation history | [Progress ledger](../progress/ledger.md) |
| Interview-oriented walkthrough | [Five-minute demo runbook](demo-runbook.md) |
| Local release verification | [Phase 13 verifier](../../scripts/verify-phase-13.ps1) |

## Deliberate limitations

This project intentionally does not claim:

- connection to a bank, card scheme, payment gateway or clearing system;
- real-money movement or regulatory compliance;
- distributed deployment or cluster-wide rate limiting;
- a production SLO, capacity figure or availability guarantee;
- broker infrastructure as a requirement for the first asynchronous boundary; or
- automatic financial correction after reconciliation.

The implementation is designed to make those boundaries explicit. A future correction workflow would require an authorised compensating ledger transaction rather than changing historical records.