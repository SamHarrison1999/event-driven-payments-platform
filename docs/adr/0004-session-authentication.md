# ADR 0004: Use server-side browser sessions

- Status: Accepted
- Date: 2026-06-24
- Decision owners: Project maintainer

## Context

The initial client is a first-party browser application operated under the
same site as the backend.

A self-contained browser token would require careful handling of:

- token storage;
- expiry;
- refresh;
- revocation;
- replay;
- logout; and
- browser script access.

That complexity does not currently provide a business benefit.

## Decision

Use Spring Security with server-side sessions persisted through Spring Session
JDBC.

Authentication uses a secure session cookie configured with:

- `HttpOnly`;
- `Secure` outside local HTTP development;
- an explicit `SameSite` policy;
- a narrow path;
- no unnecessary domain scope; and
- session rotation after successful authentication.

Keep CSRF protection enabled for state-changing browser requests.

Passwords use Spring Security's delegating password encoder with an adaptive
password-hashing algorithm and reviewed parameters.

Authorisation is enforced at service boundaries as well as HTTP routing.

Passwords, tokens, cookies and session identifiers must not appear in
application logs.

## Roles

Initial roles are:

- `CUSTOMER`;
- `OPERATIONS`;
- `RECONCILIATION_ANALYST`; and
- `ADMIN`.

Roles grant capabilities.

A role does not automatically grant ownership of all customer data.

Object-level access checks remain required.

## Consequences

### Positive

- Sessions can be revoked centrally.
- Browser credentials remain in protected cookies.
- Refresh-token complexity is avoided.
- The design fits a first-party web application.
- Logout can invalidate server-side state.

### Negative

- Session availability depends on PostgreSQL.
- Cross-origin clients would require additional design.
- CSRF configuration and testing are mandatory.
- Horizontal scaling requires shared session storage.

## Revisit triggers

Reconsider OAuth 2.0, OpenID Connect or token-based access when:

- a separately owned client is introduced;
- machine-to-machine access is required;
- an external identity provider is integrated;
- public API clients are introduced; or
- deployment topology makes the same-site session model unsuitable.

A future change requires a new ADR and migration plan.
