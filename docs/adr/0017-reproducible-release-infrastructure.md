# ADR 0017: Reproducible local release infrastructure

- Status: Accepted
- Date: 2026-07-27
- Owners: Platform engineering

## Context

The platform is an educational simulation with a Spring Boot backend, a React
browser client and PostgreSQL as its system of record. Development Compose
currently starts PostgreSQL only. Phase 13 needs a repeatable release-shaped
local runtime that demonstrates how the independently built backend and
frontend fit together without implying a production banking deployment.

## Decisions

1. Add a separate `compose.release.yaml` rather than changing the existing
   PostgreSQL development Compose contract.
2. Build the backend and frontend from committed Dockerfiles. The backend uses
   a deterministic executable JAR name and runs as an unprivileged user. The
   frontend is served by an unprivileged Nginx image.
3. Keep PostgreSQL internal to the release composition and gate backend startup
   on its health check. Expose only the frontend HTTP port to the host.
4. Serve the browser client and API through one Nginx origin. Nginx proxies the
   established `/api`, `/actuator`, `/v3` and `/swagger-ui` paths to the
   backend, preserving the browser's same-origin session and CSRF model.
5. Use synthetic local defaults only. The Compose file must accept environment
   overrides, and its educational, non-real-money scope remains unchanged.
6. Verify the release composition with a static Compose contract gate and a
   runtime smoke test. The smoke test must be safe to stop without deleting
   the named PostgreSQL volume.

## Consequences

Positive consequences:

- a clean checkout can build and run the complete platform through one Compose
  file;
- the browser and API use one origin in the release-shaped runtime;
- the backend image has a deterministic artifact and does not run as root;
- PostgreSQL readiness is explicit rather than an assumed startup order; and
- the release boundary is testable locally before portfolio packaging.

Trade-offs:

- Docker image builds require network access to the configured registries and
  Maven/npm dependency sources;
- the local defaults are intentionally not suitable for production secrets or
  internet exposure;
- Nginx is a same-origin local release boundary, not a complete production
  ingress, TLS or multi-instance platform.

## Rejected alternatives

- Replacing the existing development Compose file would make the simple
  PostgreSQL-only workflow unnecessarily heavy.
- Exposing the backend directly as the primary browser origin would bypass the
  same-origin release path being demonstrated.
- Adding Kubernetes or a cloud provider in this first batch would expand the
  scope before the local container contract is verified.
