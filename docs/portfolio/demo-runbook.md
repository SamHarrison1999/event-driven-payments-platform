# Five-minute interview demo runbook

## Purpose

Use this walkthrough to explain the engineering decisions behind the platform without presenting it as a production payment service. The strongest demonstration is a small number of verifiable workflows linked to the invariants they protect.

## Before the interview

Prepare a local checkout with Docker Desktop, Java 25, Node.js 24.18.0, pnpm 11.9.0 and PowerShell. Run the Phase 13 verifier once so the release composition has already been built and smoke-tested:

```powershell
.\scripts\verify-phase-13.ps1
```

The verifier starts the release stack, checks PostgreSQL and backend health, exercises the browser-facing origin and then tears the stack down. The default browser URL while the stack is running is:

```text
http://localhost:8080
```

Have the repository README, the [architecture diagrams](../architecture/diagrams.md) and the [progress ledger](../progress/ledger.md) open as supporting evidence.

## Minute-by-minute walkthrough

### 0:00–0:45 — Frame the project

Say:

> This is a full-stack educational simulation of payment processing and settlement reconciliation. It demonstrates reliability and security boundaries, but it does not move real money or connect to an external payment provider.

Point out the modular-monolith choice, Java 25/Spring Boot 4 backend, React/TypeScript frontend and PostgreSQL system of record.

### 0:45–1:45 — Show the payment invariant

Use the payment flow to explain that a successful payment is not just a status update:

- the request is authenticated and ownership is derived from the session;
- the idempotency key prevents a client retry from creating another payment;
- the source account is debited and destination account credited in one balanced journal;
- the payment, account snapshots, ledger posting and completed-payment outbox event commit together.

The central interview question is: “What happens if the client retries or the process fails after the database commit?” The answer is idempotency plus the transactional outbox, not a best-effort in-memory callback.

### 1:45–2:45 — Show asynchronous delivery

Explain the outbox lifecycle:

- events are claimed with bounded owner-token leases;
- publication retries are scheduled deterministically;
- exhausted failures enter a dead-letter state;
- consumers checkpoint progress durably; and
- the unique source-event identifier makes repeated delivery safe.

This is where the project demonstrates at-least-once delivery with idempotent consumers rather than claiming exactly-once distributed processing.

### 2:45–3:45 — Show reconciliation

Open the settlement section of the README or the reconciliation diagram:

- the complete bounded CSV is parsed and validated before persistence;
- identical accepted uploads are identified by the raw-byte SHA-256 fingerprint;
- payment snapshots are read through a narrow public module boundary;
- results and discrepancies are persisted atomically; and
- resolution requires a strong ETag and records the actor, decision, reason and time.

Emphasise that reconciliation is intentionally read-only with respect to payment and ledger history.

### 3:45–4:30 — Show security and operations

Mention the controls most relevant to a reviewer:

- session authentication and CSRF protection;
- service-boundary role enforcement;
- customer ownership checks;
- strict problem responses and input validation;
- no-store handling for sensitive responses;
- bounded write-route abuse protection;
- structured low-cardinality observability; and
- role-scoped audit and reporting exports.

Avoid presenting security controls as a substitute for deployment hardening or an external security review.

### 4:30–5:00 — Close with evidence and limits

Finish with:

- the Docker release composition;
- unprivileged backend and frontend containers;
- GitHub Actions repository, backend and frontend checks;
- the progress ledger and ADR history; and
- the explicit list of limitations.

A strong closing statement is:

> The value of this project is the reasoning made visible: invariants, boundaries, retries, evidence and failure handling are documented and tested. It is an educational system, not a claim to operate a real payment network.