#!/usr/bin/env bash
set -euo pipefail

repository_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
script_path="$repository_root/load-tests/payment-submission.js"
readme_path="$repository_root/load-tests/README.md"

test -f "$script_path"
test -f "$readme_path"

for pattern in \
  'constant-arrival-rate' \
  'api/v1/payments' \
  'PAYMENTS_SESSION' \
  'CSRF_TOKEN' \
  'SOURCE_ACCOUNT_ID' \
  'DESTINATION_ACCOUNT_ID' \
  'Idempotency-Key' \
  'payment_submission' \
  'http_req_failed' \
  'payment_submission_duration'; do
  grep -Fq "$pattern" "$script_path"
done

for pattern in \
  'authenticated' \
  'CSRF' \
  'disposable local' \
  'provisional harness checks' \
  'actual' \
  'grafana/k6:1.6.1'; do
  grep -Fq "$pattern" "$readme_path"
done

if grep -Eiq '(password|secret|token)[[:space:]]*[:=][[:space:]]*["'"'][^"'"']+["'"']' "$script_path"; then
  echo 'The load-test script appears to contain a hard-coded secret.' >&2
  exit 1
fi

git -C "$repository_root" diff --check
printf '%s\n' 'Phase 11 load-test static verification passed.'
