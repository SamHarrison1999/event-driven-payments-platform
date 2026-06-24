# ADR 0002: Initial technology baseline

- Status: Accepted
- Date: 2026-06-24
- Decision owners: Project maintainer

## Context

The project requires a modern, supported and reproducible technology stack.

The selected versions must be mutually compatible and suitable for a
portfolio-quality financial-system simulation.

Platform dependency management should be used where possible to avoid
unnecessary version overrides.

## Decision

Use this initial baseline:

| Component | Version |
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

Use the Java toolchain feature instead of relying only on the developer's
current `JAVA_HOME`.

Use the committed Gradle Wrapper for every backend build.

Use Corepack and the `packageManager` field in `package.json` to pin pnpm.

Use Spring Boot dependency management for dependencies including:

- Spring Framework;
- Spring Security;
- Spring Session;
- Flyway;
- PostgreSQL JDBC;
- Kafka clients;
- JUnit;
- Mockito; and
- Testcontainers.

Do not override Spring Boot-managed versions without a documented reason.

## Spring Boot line

Spring Boot 4.0.7 is selected as the initial backend baseline.

The project will not move to a later minor or major Spring Boot line until the
following integrations have executable tests:

- Spring Modulith verification;
- Springdoc OpenAPI generation;
- Flyway migrations;
- PostgreSQL integration;
- Spring Security;
- Spring Session; and
- Testcontainers.

## Compatibility gate

Phase 1 must execute tests proving that:

- Java 25 compiles and runs the application;
- Gradle 9.6.0 executes the build;
- Spring Boot starts;
- Spring Modulith verifies the module structure;
- Springdoc generates an OpenAPI document;
- Flyway migrates PostgreSQL 18;
- JUnit discovers tests; and
- Testcontainers starts the selected PostgreSQL image.

Documentation research does not replace this executable compatibility gate.

## Version-management policy

- Patch updates may be proposed through normal dependency maintenance.
- Minor framework upgrades require review of release notes and compatibility.
- Major framework upgrades require a new or amended ADR.
- Container images must use explicit tags.
- Release builds must not use floating `latest` tags.

## Consequences

### Positive

- The application uses a current LTS Java runtime.
- Backend transitive dependencies remain centrally aligned.
- Local and CI builds use the same Gradle version.
- The frontend package manager is reproducible.
- Compatibility is verified through executable tests.

### Negative

- Recent framework versions may expose integration regressions.
- Version upgrades require deliberate review.
- Supporting Java 25 may require current IDE and CI tooling.
