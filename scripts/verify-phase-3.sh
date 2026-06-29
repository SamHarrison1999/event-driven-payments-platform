#!/usr/bin/env bash

set -euo pipefail

readonly SCRIPT_DIRECTORY="$(
  cd "$(dirname "${BASH_SOURCE[0]}")" &&
    pwd
)"

readonly REPOSITORY_ROOT="$(
  cd "${SCRIPT_DIRECTORY}/.." &&
    pwd
)"

readonly REQUIRED_FILES=(
  "README.md"
  "docs/architecture/overview.md"
  "docs/progress/ledger.md"

  "backend/src/main/resources/db/migration/V5__create_customer_schema.sql"
  "backend/src/main/resources/db/migration/V6__create_account_schema.sql"
  "backend/src/main/resources/db/migration/V7__create_customer_identity_assignment.sql"
  "backend/src/main/resources/db/migration/V8__harden_phase_3_validation.sql"

  "backend/src/main/java/com/samharrison/payments/shared/GbpAmount.java"
  "backend/src/main/java/com/samharrison/payments/customer/internal/CustomerProfile.java"
  "backend/src/main/java/com/samharrison/payments/customer/internal/CustomerManagementController.java"
  "backend/src/main/java/com/samharrison/payments/customer/internal/CustomerOwnershipManagementController.java"
  "backend/src/main/java/com/samharrison/payments/account/internal/CustomerAccount.java"
  "backend/src/main/java/com/samharrison/payments/account/internal/AccountManagementController.java"
  "backend/src/main/java/com/samharrison/payments/account/internal/CustomerAccountQueryController.java"
  "backend/src/main/java/com/samharrison/payments/identity/internal/IdentitySecurityProblemHandler.java"

  "backend/src/test/java/com/samharrison/payments/customer/internal/CustomerPersistenceIntegrationTest.java"
  "backend/src/test/java/com/samharrison/payments/customer/internal/CustomerConditionalUpdateHttpIntegrationTest.java"
  "backend/src/test/java/com/samharrison/payments/account/internal/CustomerAccountPersistenceIntegrationTest.java"
  "backend/src/test/java/com/samharrison/payments/account/internal/CustomerAccountOwnershipHttpIntegrationTest.java"
  "backend/src/test/java/com/samharrison/payments/account/internal/AccountConditionalUpdateHttpIntegrationTest.java"

  "scripts/verify-phase-2.ps1"
  "scripts/verify-phase-2.sh"
  "scripts/verify-phase-3.ps1"
  "scripts/verify-phase-3.sh"
)

fail() {
  printf 'Phase 3 verification failed: %s\n' "$1" >&2
  exit 1
}

cd "${REPOSITORY_ROOT}"

printf '==> Check required Phase 3 files\n'

for file in "${REQUIRED_FILES[@]}"; do
  if [[ ! -f "${file}" ]]; then
    fail "required file is missing: ${file}"
  fi

  if [[ ! -s "${file}" ]]; then
    fail "required file is empty: ${file}"
  fi
done

printf '\n==> Validate Phase 3 documentation\n'

readonly REQUIRED_README_TEXT=(
  "Phase 3"
  "Customers and accounts"
  "verify-phase-3.ps1"
  "POST /api/v1/customers"
  "POST /api/v1/accounts"
  "If-Match"
  "application/problem+json"
  "this application does not process real money"
)

for expected_text in "${REQUIRED_README_TEXT[@]}"; do
  if ! grep -Fq "${expected_text}" README.md; then
    fail "README is missing required text: ${expected_text}"
  fi
done

readonly REQUIRED_ARCHITECTURE_TEXT=(
  "Phase 3"
  "Customer and account lifecycle"
  "optimistic concurrency"
  "Migration version 8"
  'structured `401 Unauthorized`'
  'Stale writes return `412 Precondition Failed`'
)

for expected_text in "${REQUIRED_ARCHITECTURE_TEXT[@]}"; do
  if ! grep -Fq \
    "${expected_text}" \
    docs/architecture/overview.md; then
    fail \
      "architecture overview is missing required text: ${expected_text}"
  fi
done

readonly REQUIRED_LEDGER_TEXT=(
  "Phase 3 acceptance evidence"
  "Customers and accounts | Completed"
  "Customer profiles and ownership"
  "GBP accounts and customer views"
  "Validation, security and verification"
  "Full backend test and JAR gate passes"
)

for expected_text in "${REQUIRED_LEDGER_TEXT[@]}"; do
  if ! grep -Fq \
    "${expected_text}" \
    docs/progress/ledger.md; then
    fail \
      "progress ledger is missing required text: ${expected_text}"
  fi
done

printf '\n==> Validate Flyway migration sequence\n'

mapfile -t migration_versions < <(
  for migration in \
    backend/src/main/resources/db/migration/V*__*.sql; do
    basename "${migration}" |
      sed -E 's/^V([0-9]+)__.*/\1/'
  done |
    sort -n
)

readonly expected_migration_versions=(
  1 2 3 4 5 6 7 8
)

readonly phase3_migration_versions=(
  "${migration_versions[@]:0:8}"
)

if [[ "${phase3_migration_versions[*]}" != "${expected_migration_versions[*]}" ]]; then
  fail \
    "expected Flyway migrations 1 through 8 to remain present, found: ${migration_versions[*]}"
fi

printf '\n==> Run Phase 2 baseline verification\n'

bash scripts/verify-phase-2.sh

printf '\nPhase 3 verification passed.\n'