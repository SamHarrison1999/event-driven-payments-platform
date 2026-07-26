# Payment-path load test

This directory contains the Phase 11 k6 scenario for the authenticated
`POST /api/v1/payments` path. It measures the real HTTP path, including
session authentication, CSRF validation, idempotency reservation, payment
processing and the database transaction.

The scenario deliberately does not bypass Spring Security or call an internal
service directly. It requires a prepared customer session and two active GBP
accounts owned by that customer. The source account must contain enough funds
for the planned number of unique payment requests. Use a disposable local
database and a small amount such as `1` minor unit.

## Required inputs

Set these values in the local shell only. Do not commit them or place them in
the test script:

| Variable | Meaning |
|---|---|
| `BASE_URL` | Backend base URL, default `http://localhost:8080` |
| `PAYMENTS_SESSION` | Value of the authenticated `PAYMENTS_SESSION` cookie |
| `CSRF_TOKEN` | Value returned by `GET /api/v1/identity/csrf` |
| `SOURCE_ACCOUNT_ID` | Active source account owned by the session user |
| `DESTINATION_ACCOUNT_ID` | Active destination GBP account |
| `PAYMENT_AMOUNT_MINOR_UNITS` | Positive GBP minor-unit amount, default `1` |

The session cookie and CSRF token are credentials. Treat them as short-lived
local secrets and rotate them after the run.

## Controlled local run

Start PostgreSQL and the backend using the existing project instructions. The
fixture should be created through the normal application flow or a disposable
local database setup. Then run a short smoke load before a longer measurement:

```powershell
$env:BASE_URL = 'http://localhost:8080'
$env:PAYMENTS_SESSION = '<short-lived-cookie-value>'
$env:CSRF_TOKEN = '<csrf-token-value>'
$env:SOURCE_ACCOUNT_ID = '<source-account-uuid>'
$env:DESTINATION_ACCOUNT_ID = '<destination-account-uuid>'
$env:PAYMENT_AMOUNT_MINOR_UNITS = '1'

k6 run .\load-tests\payment-submission.js `
    --vus 2 `
    --duration 10s
```

The script defaults to five arrivals per second for 30 seconds. Override the
scenario deliberately for a measurement run:

```powershell
$env:PAYMENT_RATE = '10'
$env:PAYMENT_DURATION = '60s'
$env:PAYMENT_PRE_ALLOCATED_VUS = '10'
$env:PAYMENT_MAX_VUS = '30'

k6 run .\load-tests\payment-submission.js
```

The thresholds are provisional harness checks, not published product SLOs:
at least 99% of checks must pass, fewer than 1% of HTTP requests may fail, and
the payment submission p95 must remain below two seconds for this run. The
final Phase 11 batch will record the actual environment, workload, results,
and limitations before making any performance claim.

## Docker runner

When k6 is not installed locally, the pinned k6 image can run the same script.
The backend must be reachable from the container; Docker Desktop users should
use `host.docker.internal` in `BASE_URL`:

```powershell
Get-Content .\load-tests\payment-submission.js -Raw |
    docker run --rm -i `
        -e BASE_URL=http://host.docker.internal:8080 `
        -e PAYMENTS_SESSION `
        -e CSRF_TOKEN `
        -e SOURCE_ACCOUNT_ID `
        -e DESTINATION_ACCOUNT_ID `
        -e PAYMENT_AMOUNT_MINOR_UNITS `
        grafana/k6:1.6.1 run - `
        --vus 2 `
        --duration 10s
```

The script is intentionally free of hard-coded identities, account IDs,
passwords and session data. A load result is meaningful only when the fixture,
database state, backend build, JVM settings and workload are recorded beside
the result.
