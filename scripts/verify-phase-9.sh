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
  "docs/adr/0013-settlement-and-reconciliation.md"
  "docs/progress/ledger.md"

  "backend/src/main/resources/db/migration/V16__create_settlement_import_foundation.sql"
  "backend/src/main/resources/db/migration/V17__create_settlement_reconciliation_results.sql"
  "backend/src/main/resources/db/migration/V18__create_settlement_resolution_evidence.sql"

  "backend/src/main/java/com/samharrison/payments/payment/PaymentReconciliationReader.java"
  "backend/src/main/java/com/samharrison/payments/payment/PaymentReconciliationSnapshot.java"
  "backend/src/main/java/com/samharrison/payments/payment/internal/PaymentReconciliationReaderService.java"

  "backend/src/main/java/com/samharrison/payments/reconciliation/internal/SettlementCsvParser.java"
  "backend/src/main/java/com/samharrison/payments/reconciliation/internal/SettlementImportService.java"
  "backend/src/main/java/com/samharrison/payments/reconciliation/internal/SettlementImportTransaction.java"
  "backend/src/main/java/com/samharrison/payments/reconciliation/internal/SettlementMatcher.java"
  "backend/src/main/java/com/samharrison/payments/reconciliation/internal/SettlementImportController.java"
  "backend/src/main/java/com/samharrison/payments/reconciliation/internal/SettlementDiscrepancyController.java"
  "backend/src/main/java/com/samharrison/payments/reconciliation/internal/SettlementDiscrepancyResolutionService.java"
  "backend/src/main/java/com/samharrison/payments/reconciliation/internal/SettlementResolution.java"

  "backend/src/test/java/com/samharrison/payments/reconciliation/internal/SettlementCsvParserTest.java"
  "backend/src/test/java/com/samharrison/payments/reconciliation/internal/SettlementMatcherTest.java"
  "backend/src/test/java/com/samharrison/payments/reconciliation/internal/SettlementPersistenceIntegrationTest.java"
  "backend/src/test/java/com/samharrison/payments/reconciliation/internal/SettlementImportWorkflowIntegrationTest.java"
  "backend/src/test/java/com/samharrison/payments/reconciliation/internal/SettlementImportHttpIntegrationTest.java"
  "backend/src/test/java/com/samharrison/payments/reconciliation/internal/SettlementDiscrepancyPersistenceIntegrationTest.java"
  "backend/src/test/java/com/samharrison/payments/reconciliation/internal/SettlementDiscrepancyHttpIntegrationTest.java"
  "backend/src/test/java/com/samharrison/payments/ModularityTest.java"

  "frontend/src/features/reconciliation/api/settlement.ts"
  "frontend/src/features/reconciliation/api/uploadSettlementFile.ts"
  "frontend/src/features/reconciliation/api/resolveSettlementDiscrepancy.ts"
  "frontend/src/features/reconciliation/api/settlementApi.test.ts"
  "frontend/src/features/reconciliation/components/ReconciliationWorkspace.tsx"
  "frontend/src/features/reconciliation/components/ReconciliationWorkspace.test.tsx"
  "frontend/src/features/reconciliation/components/SettlementImportPanel.tsx"
  "frontend/src/features/reconciliation/components/SettlementDiscrepancyPanel.tsx"

  "scripts/verify-phase-8.ps1"
  "scripts/verify-phase-8.sh"
  "scripts/verify-phase-9.ps1"
  "scripts/verify-phase-9.sh"
)

fail() {
  printf 'Phase 9 verification failed: %s\n' "$1" >&2
  exit 1
}

require_text() {
  local file="$1"
  local expected_text="$2"
  local description="$3"

  if ! grep -Fq "${expected_text}" "${file}"; then
    fail "${description} is missing required text: ${expected_text}"
  fi
}

cd "${REPOSITORY_ROOT}"

printf '==> Check required Phase 9 files\n'

for file in "${REQUIRED_FILES[@]}"; do
  if [[ ! -f "${file}" ]]; then
    fail "required file is missing: ${file}"
  fi

  if [[ ! -s "${file}" ]]; then
    fail "required file is empty: ${file}"
  fi
done

printf '\n==> Validate Phase 9 documentation\n'

readonly REQUIRED_README_TEXT=(
  "Settlement and reconciliation"
  "POST /api/v1/settlement-imports"
  "GET  /api/v1/settlement-discrepancies"
  "PUT  /api/v1/settlement-discrepancies/{discrepancyId}/resolution"
  "Phase 9 verification"
  "verify-phase-9.ps1"
  "verify-phase-9.sh"
)

for expected_text in "${REQUIRED_README_TEXT[@]}"; do
  require_text "README.md" "${expected_text}" "README"
done

readonly REQUIRED_ARCHITECTURE_TEXT=(
  "Phase 9 implements"
  "Migration version 16"
  "Migration version 17"
  "Migration version 18"
  "database-protected accepted settlement match"
  "one-time discrepancy resolution"
  "Payment processing and settlement reconciliation are separate state machines"
)

for expected_text in "${REQUIRED_ARCHITECTURE_TEXT[@]}"; do
  require_text \
    "docs/architecture/overview.md" \
    "${expected_text}" \
    "architecture overview"
done

readonly REQUIRED_LEDGER_TEXT=(
  "Phase 9 acceptance evidence"
  "Exact bounded CSV contract is implemented | Completed"
  "Import, rows, results and discrepancies commit atomically | Completed"
  'Discrepancy resolution uses strong ETags and `If-Match` | Completed'
  "React analyst workflow passes frontend tests | Completed"
  "Phase 9 PowerShell and Bash verifiers exist"
  "Cumulative Phase 9 verifier passes"
)

for expected_text in "${REQUIRED_LEDGER_TEXT[@]}"; do
  require_text \
    "docs/progress/ledger.md" \
    "${expected_text}" \
    "progress ledger"
done

printf '\n==> Validate Phase 9 implementation contracts\n'

readonly CSV_PARSER_TEXT=(
  "MAX_FILE_SIZE_BYTES = 1_048_576"
  "MAX_DATA_ROWS = 1_000"
  "UTF8_BOM_NOT_ALLOWED"
  "MALFORMED_UTF8"
  "MessageDigest"
  '"SHA-256"'
)

for expected_text in "${CSV_PARSER_TEXT[@]}"; do
  require_text \
    "backend/src/main/java/com/samharrison/payments/reconciliation/internal/SettlementCsvParser.java" \
    "${expected_text}" \
    "settlement CSV parser"
done

require_text \
  "backend/src/main/java/com/samharrison/payments/payment/PaymentReconciliationReader.java" \
  "MAX_PAYMENT_IDS = 1_000" \
  "payment reconciliation reader"

readonly MATCHER_TEXT=(
  "PAYMENT_NOT_FOUND"
  "PAYMENT_NOT_COMPLETED"
  "CURRENCY_MISMATCH"
  "AMOUNT_MISMATCH"
  "SETTLED_BEFORE_COMPLETION"
)

for expected_text in "${MATCHER_TEXT[@]}"; do
  require_text \
    "backend/src/main/java/com/samharrison/payments/reconciliation/internal/SettlementMatcher.java" \
    "${expected_text}" \
    "settlement matcher"
done

require_text \
  "backend/src/main/java/com/samharrison/payments/reconciliation/internal/SettlementImportTransaction.java" \
  "DUPLICATE_PAYMENT_SETTLEMENT" \
  "settlement import transaction"

readonly V16_TEXT=(
  "CREATE TABLE settlement_import"
  "CREATE TABLE settlement_record"
  "UNIQUE (raw_file_sha256)"
  "trg_settlement_record_immutable"
  "BEFORE UPDATE OR DELETE"
)

for expected_text in "${V16_TEXT[@]}"; do
  require_text \
    "backend/src/main/resources/db/migration/V16__create_settlement_import_foundation.sql" \
    "${expected_text}" \
    "migration V16"
done

readonly V17_TEXT=(
  "CREATE TABLE settlement_result"
  "CREATE TABLE settlement_match_claim"
  "CREATE TABLE settlement_discrepancy"
  "trg_settlement_result_immutable"
  "trg_settlement_claim_immutable"
)

for expected_text in "${V17_TEXT[@]}"; do
  require_text \
    "backend/src/main/resources/db/migration/V17__create_settlement_reconciliation_results.sql" \
    "${expected_text}" \
    "migration V17"
done

readonly V18_TEXT=(
  "CREATE TABLE settlement_resolution"
  "trg_settlement_resolution_immutable"
  "trg_settlement_discrepancy_lifecycle"
  "CREATE CONSTRAINT TRIGGER"
)

for expected_text in "${V18_TEXT[@]}"; do
  require_text \
    "backend/src/main/resources/db/migration/V18__create_settlement_resolution_evidence.sql" \
    "${expected_text}" \
    "migration V18"
done

readonly RESOLUTION_SERVICE_TEXT=(
  "RECONCILIATION_ANALYST"
  "ADMIN"
  "SettlementResolution"
)

for expected_text in "${RESOLUTION_SERVICE_TEXT[@]}"; do
  require_text \
    "backend/src/main/java/com/samharrison/payments/reconciliation/internal/SettlementDiscrepancyResolutionService.java" \
    "${expected_text}" \
    "discrepancy resolution service"
done

readonly FRONTEND_RESOLUTION_TEXT=(
  "getCsrfHeaders"
  "csrfHeaders.set('If-Match'"
  "expectedStatus: 200"
)

for expected_text in "${FRONTEND_RESOLUTION_TEXT[@]}"; do
  require_text \
    "frontend/src/features/reconciliation/api/resolveSettlementDiscrepancy.ts" \
    "${expected_text}" \
    "frontend resolution API"
done

require_text \
  "frontend/src/features/reconciliation/components/SettlementImportPanel.tsx" \
  "UTF-8 CSV, 1–1,000 rows, maximum 1 MiB." \
  "settlement import panel"

require_text \
  "frontend/src/features/reconciliation/components/SettlementImportPanel.tsx" \
  "Raw SHA-256" \
  "settlement import panel"

printf '\n==> Validate Flyway migration sequence\n'

mapfile -t migration_versions < <(
  find backend/src/main/resources/db/migration \
    -maxdepth 1 \
    -type f \
    -name 'V*__*.sql' \
    -printf '%f\n' |
  sed -E 's/^V([0-9]+)__.*/\1/' |
  sort -n
)

readonly expected_versions="$(
  seq 1 18 |
    paste -sd, -
)"

readonly actual_versions="$(
  printf '%s\n' "${migration_versions[@]}" |
    paste -sd, -
)"

if [[ "${actual_versions}" != "${expected_versions}" ]]; then
  fail "expected exactly Flyway migrations 1 through 18, found: ${migration_versions[*]}"
fi

printf '\n==> Run Phase 8 cumulative baseline\n'
bash scripts/verify-phase-8.sh

printf '\n==> Check unstaged whitespace\n'
git diff --check

printf '\n==> Check staged whitespace\n'
git diff --cached --check

printf '\nPhase 9 verification passed.\n'
