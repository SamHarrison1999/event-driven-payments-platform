# ADR 0007: Use a length-first password policy and versioned PBKDF2 hashes

- Status: Accepted
- Date: 2026-06-24
- Decision owners: Project maintainer

## Context

The platform initially uses passwords as a single authentication factor.

ADR 0004 requires:

- adaptive one-way password hashing;
- a delegating password encoder;
- secure comparison during authentication; and
- protection of passwords and hashes from application logs.

The exact password policy and current encoding algorithm were not previously
specified.

The application needs a policy that:

- supports long passphrases;
- does not impose arbitrary character-composition rules;
- handles Unicode consistently;
- rejects commonly expected passwords;
- stores only one-way hashes; and
- can migrate to a different encoding algorithm later.

## Decision

### Password acceptance

New passwords must:

- contain at least 15 Unicode code points;
- contain no more than 128 Unicode code points;
- contain at least one non-whitespace character; and
- not exactly match an entry in the local password blocklist.

The application:

- accepts spaces;
- accepts Unicode;
- does not require uppercase letters, lowercase letters, digits or symbols;
- does not trim passwords;
- normalises passwords to Unicode NFC before blocklist comparison and hashing;
- compares blocklist entries case-insensitively; and
- compares the complete password rather than rejecting blocklisted substrings.

The initial local blocklist is deliberately small and deterministic for the
educational version. A broader maintained compromised-password source will be
considered during security hardening.

Raw passwords must:

- remain transient;
- never be persisted;
- never be included in logs;
- never be included in exception messages; and
- never be returned through an API.

### Password storage

Use Spring Security's `DelegatingPasswordEncoder`.

The current encoding identifier is:

```text
pbkdf2@SpringSecurity_v5_8
```

New hashes use:

```
{pbkdf2@SpringSecurity_v5_8}<encoded-password>
```

The current implementation uses Spring Security's reviewed PBKDF2 defaults.

The encoding identifier remains part of the stored value so a later algorithm\
or parameter change can retain support for existing hashes while creating new\
hashes with the replacement configuration.

### Verification

Password-policy validation applies when establishing or changing a password.

Authentication verifies the complete supplied password after NFC\
normalisation. Authentication does not reapply the latest creation policy,\
because a later policy change must not unexpectedly prevent an existing user\
from authenticating.

Invalid, missing or unsupported stored hashes fail authentication safely.

Consequences
------------

### Positive

-   Long passphrases are supported.
-   Password-manager generated passwords are supported.
-   Unicode input has a stable representation before hashing.
-   Password hashes are salted and one-way.
-   Stored hashes identify their encoding format.
-   Future encoding upgrades have an explicit migration path.
-   The policy avoids arbitrary composition requirements.

### Negative

-   PBKDF2 verification intentionally consumes processing time.
-   The local blocklist is not a replacement for a maintained compromised\
    password database.
-   Password normalisation must remain consistent across registration, login and\
    password changes.
-   Authentication rate limiting remains necessary.

Revisit triggers
----------------

Revisit this decision when:

-   multi-factor authentication is introduced;
-   an external identity provider is introduced;
-   password-hashing performance measurements justify parameter changes;
-   a maintained compromised-password service or corpus is integrated;
-   Spring Security changes its recommended encoders or parameters; or
-   the security-hardening phase identifies a stronger supported approach.

A future encoding change requires an ADR, compatibility tests and a migration\
plan.
