# ADR 0016: Security hardening and threat model boundaries

- Status: Accepted
- Date: 2026-07-26
- Owners: Platform engineering

## Context

The platform is an educational, browser-based simulation with server-side
PostgreSQL sessions, CSRF protection, role-based access control and several
bounded data-import and reporting endpoints. Those controls protect the
application's business invariants, but a portfolio-ready release also needs
explicit defence-in-depth for browser responses, abuse of public endpoints,
resource exhaustion, dependency drift and accidental disclosure.

The system is not a public banking service and must not claim production
financial-system security. Phase 12 therefore concentrates on controls that
are source-owned, testable and useful for the deployed simulation.

## Decisions

1. Keep server-side sessions and synchroniser-token CSRF protection. Do not
   replace them with browser-persisted bearer tokens.
2. Add an allow-listed security-header filter for API responses. It emits
   `nosniff`, frame denial, restrictive referrer and permissions policies, a
   JSON-safe content-security policy, and HSTS only when the request is HTTPS.
3. Add a bounded in-memory fixed-window limiter for sensitive write routes.
   The key is the direct remote address, the map has a fixed maximum size, and
   entries expire. A distributed deployment must move this state to a shared
   store or an edge gateway before claiming cluster-wide enforcement.
4. Keep settlement-file limits at both the web multipart boundary and the
   parser boundary. The parser remains authoritative and continues to reject
   malformed UTF-8, control characters, BOMs, oversized files and excess rows.
5. Do not log request bodies, query strings, cookies, authorisation headers,
   CSRF tokens, filenames or personal data in request-completion events.
6. Add focused regression tests for security headers, rate-limit decisions,
   upload boundaries, CSRF, object-level access control and secret-free
   logging. Add CI dependency review and CodeQL analysis; these supplement,
   rather than replace, manual review.

## Consequences

Positive consequences:

- common browser-based response attacks receive explicit defence-in-depth;
- login, registration, payment and settlement-import abuse is bounded in one
  process;
- large multipart requests are rejected before application parsing;
- the security claims are backed by executable tests and documented limits;
- the threat model is honest about the educational scope and single-process
  limiter boundary.

Trade-offs:

- the in-memory limiter is intentionally not a distributed rate limiter;
- strict API CSP headers are not applied to Swagger UI, which remains a local
  development aid;
- dependency and CodeQL checks run in GitHub Actions and require the platform's
  normal CI permissions and network access.

## Rejected alternatives

- Redis was not introduced solely for rate limiting; it would add deployment
  complexity without a current multi-instance runtime.
- JWT access tokens were not introduced; they would widen browser credential
  exposure and duplicate the established session design.
- A generic WAF was not treated as an application control; edge protection may
  be added in Phase 13, but source-owned validation remains mandatory.
