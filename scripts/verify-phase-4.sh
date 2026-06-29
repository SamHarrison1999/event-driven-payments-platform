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
  "docs/adr/0008-gbp-minor-units-and-ledger-entry-model.md"
  "docs/progress/ledger.md"

  "backend/src/main/resources/db/migration/V9__create_double_entry_ledger_schema.sql"
  "backend/src/main/resources/db/migration/V10__enforce_ledger_invariants.sql"

  "backend/src/main/java/com/samharrison/payments/ledger/LedgerPostingService.java"
  "backend/src/main/java/com/samharrison/payments/ledger/LedgerQueryService.java"
  "backend/src/main/java/com/samharrison/payments/ledger/LedgerPostingCommand.java"
  "backend/src/main/java/com/samharrison/payments/ledger/LedgerPostingEntry.java"
  "backend/src/main/java/com/samharrison/payments/ledger/PostedLedgerTransaction.java"
  "backend/src/main/java/com/samharrison/payments/ledger/LedgerBalanceVerification.java"
  "backend/src/main/java/com/samharrison/payments/ledger/internal/LedgerTransaction.java"
  "backend/src/main/java/com/samharrison/payments/ledger/internal/LedgerPersistenceStore.java"

  "backend/src/test/java/com/samharrison/payments/ledger/internal/LedgerTransactionTest.java"
  "backend/src/test/java/com/samharrison/payments/ledger/internal/LedgerPersistenceIntegrationTest.java"
  "backend/src/test/java/com/samharrison/payments/ledger/LedgerPostingServiceIntegrationTest.java"
  "backend/src/test/java/com/samharrison/payments/ledger/internal/LedgerDatabaseInvariantIntegrationTest.java"
  "backend/src/test/java/com/samharrison/payments/ledger/LedgerQueryServiceIntegrationTest.java"

  "scripts/verify-phase-3.ps1"
  "scripts/verify-phase-3.sh"
  "scripts/verify-phase-4.ps1"
  "scripts/verify-phase-4.sh"
)

fail() {
  printf 'Phase 4 verification failed: %s\n' "$1" >&2
  exit 1
}

cd "${REPOSITORY_ROOT}"

printf '==> Check required Phase 4 files\n'

for file in "${REQUIRED_FILES[@]}"; do
  if [[ ! -f "${file}" ]]; then
    fail "required file is missing: ${file}"
  fi

  if [[ ! -s "${file}" ]]; then
    fail "required file is empty: ${file}"
  fi
done

printf '\n==> Validate Phase 4 documentation\n'

readonly REQUIRED_README_TEXT=(
  "Phase 4"
  "Double-entry ledger"
  "verify-phase-4.ps1"
  'explicit `DEBIT` and `CREDIT`'
  "deferred PostgreSQL balance verification"
  "snapshot-versus-ledger verification"
  "this application does not process real money"
)

for expected_text in "${REQUIRED_README_TEXT[@]}"; do
  if ! grep -Fq "${expected_text}" README.md; then
    fail "README is missing required text: ${expected_text}"
  fi
done

readonly REQUIRED_ARCHITECTURE_TEXT=(
  "Phase 4"
  "Ledger posting"
  "Migration version 9"
  "Migration version 10"
  "deferred PostgreSQL balance constraint"
  "Posted headers and entries cannot be updated or deleted"
  "Ledger-derived account totals"
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
  "Phase 4 acceptance evidence"
  "Double-entry ledger | Current"
  "Ledger domain and posting"
  "PostgreSQL integrity and immutability"
  "Queries, verification and architecture"
  "Focused Phase 4 ledger suite passes"
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
  1 2 3 4 5 6 7 8 9 10
)

if [[ "${migration_versions[*]}" != "${expected_migration_versions[*]}" ]]; then
  fail \
    "expected Flyway migrations 1 through 10 exactly, found: ${migration_versions[*]}"
fi

printf '\n==> Run Phase 3 baseline verification\n'

bash scripts/verify-phase-3.sh

printf '\nPhase 4 verification passed.\n'