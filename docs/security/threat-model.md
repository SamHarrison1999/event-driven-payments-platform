# Phase 12 threat model

## Scope and trust boundaries

The application is a browser client talking to a Spring Boot modular monolith.
The backend owns authentication, authorisation, CSRF validation, business
invariants, persistence and audit evidence. PostgreSQL is trusted for
durability and database constraints. The browser, uploaded settlement files,
HTTP headers and request bodies are untrusted.

The model covers the educational simulation only. It does not cover real bank
connectivity, card networks, production identity providers, cloud network
controls or a multi-region deployment.

The main boundaries are:

| Boundary | Untrusted input or actor | Primary controls |
|---|---|---|
| Browser to API | Anonymous or authenticated browser, forged headers, replayed requests | Server-side session, CSRF, validation, security headers, rate limits |
| Identity endpoints | Anonymous attacker, credential-stuffing client | Password policy, BCrypt, lockout, per-IP limits, generic failures |
| Customer/payment APIs | Authenticated user attempting cross-owner access | Method security, ownership queries, idempotency, immutable ledger rules |
| Settlement import | Analyst/admin supplied bytes and filename | Multipart and parser limits, strict UTF-8 CSV schema, row/field bounds |
| Reporting/downloads | Analyst/admin queries and CSV consumers | Role filtering, bounded windows/rows, fixed typed exports, no-store/nosniff |
| Logs and telemetry | Accidental disclosure through diagnostics | Allow-listed structured fields, no request-body/cookie/header logging |
| Dependencies and CI | Vulnerable or compromised dependency/toolchain | Locked files, dependency review, CodeQL, reproducible verification |

## Abuse cases and mitigations

| Threat | Example | Mitigations | Residual risk |
|---|---|---|---|
| Replay or duplicate submission | Re-send a payment or admin replay request | Scoped idempotency, CSRF, state transitions, optimistic conditions, audit evidence | A stolen authenticated session remains usable until expiry or invalidation |
| Broken object-level authorisation | Customer reads another customer's payment or account | Authenticated-principal ownership queries and role-gated service methods | Correctness still depends on every new endpoint using the service boundary |
| Credential abuse | Repeated login attempts or registration flooding | Password policy, account lockout, bounded per-IP write limits, generic security failures | In-memory limits do not coordinate across instances or trusted proxies |
| Injection | Malformed JSON, CSV, identifiers or report parameters | Reject unknown JSON fields, Bean Validation, allow-listed query fields, typed CSV parser, parameterised SQL | New code must preserve these patterns |
| Resource exhaustion | Huge multipart body, too many CSV rows, unbounded report query | Multipart caps, parser caps, bounded pagination/export windows, request limiter | A reverse proxy and container quota are still required in deployment |
| Sensitive-data disclosure | Password, session cookie, CSRF token, CSV row or stack trace in logs/API | Allow-listed request logs, no-store responses, generic problem details, security tests | Infrastructure logs and operator access remain outside this repository |
| Browser response abuse | Framing, MIME confusion, permissive referrer or capability access | Explicit API security headers and HTTPS-only HSTS | Swagger UI is intentionally a development surface and is not the public API |
| Dependency compromise | Vulnerable transitive library or unsafe source change | Dependency review, CodeQL, lockfiles, CI and manual release review | Automated tools can miss logic flaws and supply-chain attacks |

## Security regression evidence

Phase 12 is complete only when the focused backend security tests, frontend
tests, cumulative phase verifiers, dependency/static-analysis checks and
whitespace checks pass. Rate-limit measurements are implementation evidence,
not a production capacity or availability guarantee.
