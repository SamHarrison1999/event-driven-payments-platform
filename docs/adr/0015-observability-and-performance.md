# ADR 0015: Observability and performance foundations

- Status: Accepted
- Date: 2026-07-26
- Owners: Platform engineering

## Context

The platform has correlation identifiers and Actuator health endpoints, but
its diagnostics are not yet suitable for a repeatable observability or
performance investigation. Phase 11 must expose useful operational signals
without logging credentials, session identifiers, payment bodies, settlement
files or unbounded identifiers.

The application remains an educational modular monolith. This decision does
not introduce a broker, a production deployment topology or a claim that the
system processes real money.

## Decisions

### Structured logs

Spring Boot ECS structured console logging is the default diagnostic format.
The existing `X-Correlation-ID` value remains the request correlation key and
is carried through the logging MDC. Request completion events add only the
HTTP method, matched route, response status and elapsed milliseconds. Request
bodies, query strings, cookies, credentials and raw payment or settlement data
are excluded.

### Metrics

Micrometer remains the instrumentation API and the Prometheus registry is
provided for local scraping. The first business metrics cover payment
submissions, terminal outcomes, idempotency replays, concurrency retries and
processing duration. Names use stable low-cardinality dimensions; payment,
account, actor and correlation identifiers are never metric labels.

### Health and access

Liveness reports process health. Readiness includes the readiness state and the
PostgreSQL health contributor, so traffic is not considered ready when the
database dependency is unavailable. Health remains safe for anonymous probe
use. Metrics and Prometheus endpoints require the `ADMIN` role.

### Incremental scope

Distributed tracing, controlled failure simulation, load testing and measured
performance evidence are separate implementation batches. This keeps each
batch reviewable and prevents an unmeasured performance claim from being
presented as an implemented capability.

### Tracing

Micrometer Observation is the application-facing instrumentation API and
OpenTelemetry with OTLP is the trace implementation. Spring Boot creates HTTP
observations automatically; payment processing adds one bounded custom
observation named `platform.payment.processing`. Sampling and the OTLP
endpoint are configuration-driven so local tests do not require a running
collector. No payment body, account identifier or session value is attached
as a span attribute.

### Controlled failure simulation

The failure simulator is disabled by default using
`platform.failure-simulation.enabled`. When explicitly enabled, only an
administrator can configure it. Plans are held in memory, have a configured
maximum delay, use a small allow-list of deterministic modes, and exclude the
control endpoint from interception. The simulator returns `503` for forced
failure modes and applies payment-only failures only to payment routes. It is
an educational resilience-testing aid, not production fault injection.

## Consequences

The platform can now be inspected using machine-readable logs, standard
Prometheus scraping and trace observations while preserving the existing
security boundaries. The additional metrics and the failure simulator are
in-process; trace export is optional and configuration-driven. Later batches
must add reproducible load-test evidence without weakening the redaction and
cardinality rules.
