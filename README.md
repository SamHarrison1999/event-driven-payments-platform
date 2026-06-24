# Event-Driven Payments and Reconciliation Platform

A portfolio-quality simulation of payment processing, double-entry ledgering,
asynchronous event delivery and settlement reconciliation.

> **Educational system:** this application does not process real money and
> must not be used as a banking, payment-processing or accounting product.

## Project status

Current phase:

**Phase 0 — Architecture and repository foundation**

No executable application has been implemented yet.

See the [progress ledger](docs/progress/ledger.md) for the verified project
status.

## Product objective

The completed platform will allow authorised users to:

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

## Architecture

The system begins as a modular monolith consisting of:

- a Java and Spring Boot backend;
- Spring Modulith module verification;
- a React and TypeScript frontend;
- PostgreSQL as the system of record;
- an explicit double-entry ledger;
- a transactional outbox; and
- a Kafka-compatible broker introduced when asynchronous use cases exist.

The architecture is described in
[docs/architecture/overview.md](docs/architecture/overview.md).

Major decisions are recorded as Architecture Decision Records under
[docs/adr](docs/adr).

## Initial technology baseline

| Technology | Version |
|---|---:|
| Java | 25 LTS |
| Spring Boot | 4.0.7 |
| Spring Modulith | 2.0.7 |
| Springdoc OpenAPI | 3.0.3 |
| Gradle Wrapper | 9.6.0 |
| PostgreSQL | 18.4 |
| Node.js | 24.18.0 LTS |
| pnpm | 11.9.0 |
| React | 19.2 |
| TypeScript | 6 |
| Vite | 8 |
| Playwright | 1.61 |

Dependencies managed by Spring Boot will not be independently overridden
without a documented reason.

## Planned repository layout

```text
backend/          Spring Boot modular monolith
frontend/         React application and Playwright tests
infrastructure/   Docker Compose and deployment assets
load-tests/       Payment-path performance tests
docs/             Architecture, security and project evidence
scripts/          Reproducible verification commands
```

Executable directories are introduced only in the phase that implements them.

## Phase 0 verification

From Git Bash, WSL, Linux or macOS:

```bash
chmod +x scripts/verify-phase-0.sh
./scripts/verify-phase-0.sh
```

From PowerShell with WSL:

```powershell
wsl bash scripts/verify-phase-0.sh
```

A successful run prints:

```text
Phase 0 repository checks passed.
```

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
