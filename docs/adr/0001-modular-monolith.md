# ADR 0001: Begin as a modular monolith

- Status: Accepted
- Date: 2026-06-24
- Decision owners: Project maintainer

## Context

The platform contains several meaningful financial and operational domains.

However, version 1 is developed and operated by one person.

Starting with independently deployed services would introduce:

- distributed transactions;
- network contracts;
- service discovery;
- multiple deployment pipelines;
- broader observability requirements;
- additional local infrastructure; and
- more complex failure modes.

Those costs would be introduced before the core financial rules exist.

A conventional unstructured monolith would avoid deployment complexity but
would make future extraction difficult.

## Decision

Build one Spring Boot deployable organised into explicit domain modules.

Use Spring Modulith to:

- discover modules;
- verify permitted dependencies;
- detect cycles;
- test modules independently; and
- generate architecture evidence where useful.

Each module exposes an intentional public API.

Other modules must not directly access its internal repositories, persistence
entities or implementation classes.

The React frontend remains a separate build artefact but is not a separate
business service.

Asynchronous consumers may be extracted only after their use case, ownership,
scaling or failure-isolation needs justify independent deployment.

## Consequences

### Positive

- Financial transactions remain local to PostgreSQL.
- Development remains reproducible.
- Deployment remains understandable.
- Module boundaries remain executable and testable.
- Future extraction has identifiable seams.
- The project demonstrates architectural restraint.

### Negative

- All synchronous modules initially share one process.
- A backend process failure affects all backend modules.
- Scaling initially applies to the complete backend.
- Discipline is required to prevent inappropriate database coupling.

## Revisit triggers

Consider extracting a module when at least one of these conditions is true:

- it needs materially different scaling;
- it has an independent availability requirement;
- it requires a separate deployment cadence;
- it presents a distinct security boundary;
- its failures must be isolated from payment posting;
- its technology requirements differ significantly; or
- module interaction is already event-based and has a stable contract.

Extraction is not justified solely to describe the project as microservices.
