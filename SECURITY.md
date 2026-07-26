# Security policy

## Educational-use boundary

This repository contains an educational financial-system simulation.

It does not process real money and is not approved for production financial,
banking, payment-processing or accounting use.

Do not enter real customer records, credentials, bank details, settlement data
or payment information into the application.

## Reporting a vulnerability

Use the repository's private vulnerability-reporting feature when available.

Do not disclose an unpatched vulnerability in a public issue.

A useful vulnerability report should include:

- the affected component;
- the conditions required to reproduce the problem;
- the potential impact;
- a minimal reproduction;
- relevant logs with sensitive values removed; and
- a suggested remediation, when known.

## Supported versions

| Version | Security updates |
|---|---|
| Latest tagged release | Supported |
| Unreleased development branches | Best effort |
| Older tagged releases | Not supported |

## Secret-handling policy

The repository must not contain:

- passwords;
- session cookies;
- CSRF tokens;
- access tokens;
- refresh tokens;
- private keys;
- database connection strings containing credentials;
- real personal information; or
- production settlement files.

Local secrets belong in ignored environment files.

Committed example files must contain only clearly marked demonstration values.

## Logging policy

Application logs must not contain:

- plaintext passwords;
- password hashes;
- session identifiers;
- CSRF tokens;
- complete authentication headers;
- private keys;
- complete settlement-file rows;
- unnecessary personal information; or
- internal stack traces returned to untrusted API clients.

Correlation identifiers and internal entity identifiers may be logged when
they do not expose credentials or personal information.

## Dependency policy

Dependencies are version-locked.

Automated scanning supplements, but does not replace, manual review of:

- release notes;
- security advisories;
- transitive dependencies;
- licence implications;
- framework compatibility; and
- changes to runtime requirements.

## Security testing

The completed platform will include:

- authentication and authorisation tests;
- object-level access-control tests;
- CSRF tests;
- replay and duplicate-submission tests;
- input-validation tests;
- sensitive-log tests;
- dependency scanning;
- container scanning; and
- a documented threat model.

Phase 12 adds explicit API security headers, bounded sensitive-route rate
limiting, strict multipart upload limits, focused security regression tests,
`scripts/verify-phase-12.*`, GitHub dependency review and CodeQL analysis. The
rate limiter is intentionally in-memory and single-process; it is not a
substitute for a shared store or edge control in a multi-instance deployment.
