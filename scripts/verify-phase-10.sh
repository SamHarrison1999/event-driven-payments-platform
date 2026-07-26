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
  "docs/adr/0014-audit-and-operational-reporting.md"
  "docs/progress/ledger.md"

  "backend/src/main/resources/db/migration/V19__create_business_audit_journal.sql"
  "backend/src/main/resources/db/migration/V20__add_normalized_audit_search_indexes.sql"
  "backend/src/main/resources/db/migration/V21__add_operational_reporting_indexes.sql"

  "backend/src/main/java/com/samharrison/payments/audit/BusinessAuditEventRequest.java"
  "backend/src/main/java/com/samharrison/payments/audit/BusinessAuditEvents.java"
  "backend/src/main/java/com/samharrison/payments/audit/BusinessAuditRecorder.java"
  "backend/src/main/java/com/samharrison/payments/audit/BusinessAuditEvidenceReader.java"
  "backend/src/main/java/com/samharrison/payments/audit/internal/BusinessAuditMetadataSerializer.java"
  "backend/src/main/java/com/samharrison/payments/audit/internal/BusinessAuditRecorderService.java"

  "backend/src/main/java/com/samharrison/payments/identity/IdentitySecurityAuditReader.java"
  "backend/src/main/java/com/samharrison/payments/outbox/OutboxReplayAuditReader.java"
  "backend/src/main/java/com/samharrison/payments/reconciliation/SettlementResolutionAuditReader.java"
  "backend/src/main/java/com/samharrison/payments/payment/PaymentOperationalReportReader.java"
  "backend/src/main/java/com/samharrison/payments/reconciliation/OperationalReconciliationReportReader.java"

  "backend/src/main/java/com/samharrison/payments/reporting/internal/AuditCursorCodec.java"
  "backend/src/main/java/com/samharrison/payments/reporting/internal/AuditSearchFilter.java"
  "backend/src/main/java/com/samharrison/payments/reporting/internal/AuditSearchService.java"
  "backend/src/main/java/com/samharrison/payments/reporting/internal/AuditEventController.java"
  "backend/src/main/java/com/samharrison/payments/reporting/internal/ReportWindow.java"
  "backend/src/main/java/com/samharrison/payments/reporting/internal/CsvDocumentWriter.java"
  "backend/src/main/java/com/samharrison/payments/reporting/internal/OperationalSummaryService.java"
  "backend/src/main/java/com/samharrison/payments/reporting/internal/ReportExportService.java"
  "backend/src/main/java/com/samharrison/payments/reporting/internal/ReportController.java"

  "backend/src/test/java/com/samharrison/payments/audit/internal/BusinessAuditPersistenceIntegrationTest.java"
  "backend/src/test/java/com/samharrison/payments/reporting/internal/AuditSearchHttpIntegrationTest.java"
  "backend/src/test/java/com/samharrison/payments/reporting/internal/OperationalReportingHttpIntegrationTest.java"
  "backend/src/test/java/com/samharrison/payments/reporting/internal/CsvDocumentWriterTest.java"
  "backend/src/test/java/com/samharrison/payments/ModularityTest.java"

  "frontend/src/features/reporting/api/auditEvent.ts"
  "frontend/src/features/reporting/api/downloadReport.ts"
  "frontend/src/features/reporting/api/reportingApi.test.ts"
  "frontend/src/features/reporting/components/AuditReportingWorkspace.tsx"
  "frontend/src/features/reporting/components/AuditReportingWorkspace.test.tsx"
  "frontend/src/features/reporting/components/AuditSearchPanel.tsx"
  "frontend/src/features/reporting/components/OperationalSummaryPanel.tsx"
  "frontend/src/features/reporting/components/ReportDownloadsPanel.tsx"
  "frontend/src/features/reporting/hooks/useReportingSessionExpiry.ts"

  "scripts/verify-phase-9.ps1"
  "scripts/verify-phase-9.sh"
  "scripts/verify-phase-10.ps1"
  "scripts/verify-phase-10.sh"
)

fail() {
  printf 'Phase 10 verification failed: %s\n' "$1" >&2
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

printf '==> Check required Phase 10 files\n'

for file in "${REQUIRED_FILES[@]}"; do
  if [[ ! -f "${file}" ]]; then
    fail "required file is missing: ${file}"
  fi

  if [[ ! -s "${file}" ]]; then
    fail "required file is empty: ${file}"
  fi
done

printf '\n==> Validate Phase 10 documentation\n'

readonly REQUIRED_README_TEXT=(
  "Audit and operational reporting"
  "GET /api/v1/audit-events"
  "GET /api/v1/reports/operational-summary"
  "GET /api/v1/reports/audit-events.csv"
  "GET /api/v1/reports/payments.csv"
  "GET /api/v1/reports/settlements.csv"
  "GET /api/v1/reports/reconciliation.csv"
  "Phase 10 verification"
  "verify-phase-10.ps1"
  "verify-phase-10.sh"
)

for expected_text in "${REQUIRED_README_TEXT[@]}"; do
  require_text "README.md" "${expected_text}" "README"
done

readonly REQUIRED_ARCHITECTURE_TEXT=(
  "Phase 10 implements"
  "Migration version 19 creates"
  "Migration version 20 adds"
  "Migration version 21 adds"
  "normalized read-only audit projections"
  "bounded, formula-safe CSV report exports"
  "Visibility is applied in the service query before pagination"
)

for expected_text in "${REQUIRED_ARCHITECTURE_TEXT[@]}"; do
  require_text \
    "docs/architecture/overview.md" \
    "${expected_text}" \
    "architecture overview"
done

readonly REQUIRED_LEDGER_TEXT=(
  "Phase 10 acceptance criteria"
  "Canonical business-audit events are append-only | Completed"
  "Audit writes commit atomically with their business mutation | Completed"
  "Role visibility is enforced before paging and aggregation | Completed"
  "CSV uses fixed typed columns and RFC 4180 records | Completed"
  "Role-gated audit and reporting workspace is implemented | Completed"
  "PowerShell and Bash Phase 10 verifiers exist"
  "Cumulative Phase 10 verifier passes"
)

for expected_text in "${REQUIRED_LEDGER_TEXT[@]}"; do
  require_text \
    "docs/progress/ledger.md" \
    "${expected_text}" \
    "progress ledger"
done

printf '\n==> Validate Phase 10 implementation contracts\n'

readonly BUSINESS_AUDIT_REQUEST_TEXT=(
  "MAX_METADATA_ENTRIES = 16"
  "sourceEventIdentifier"
  "TreeMap"
  "METADATA_KEY_PATTERN"
)

for expected_text in "${BUSINESS_AUDIT_REQUEST_TEXT[@]}"; do
  require_text \
    "backend/src/main/java/com/samharrison/payments/audit/BusinessAuditEventRequest.java" \
    "${expected_text}" \
    "business audit request"
done

readonly BUSINESS_AUDIT_SERIALIZER_TEXT=(
  "MAX_METADATA_BYTES = 4_096"
  "writeValueAsString"
  "StandardCharsets.UTF_8"
)

for expected_text in "${BUSINESS_AUDIT_SERIALIZER_TEXT[@]}"; do
  require_text \
    "backend/src/main/java/com/samharrison/payments/audit/internal/BusinessAuditMetadataSerializer.java" \
    "${expected_text}" \
    "business audit metadata serializer"
done

readonly V19_TEXT=(
  "CREATE TABLE business_audit_event"
  "source_event_identifier VARCHAR(128) NOT NULL"
  "validate_business_audit_metadata"
  "trg_business_audit_event_immutable"
  "BEFORE UPDATE OR DELETE"
)

for expected_text in "${V19_TEXT[@]}"; do
  require_text \
    "backend/src/main/resources/db/migration/V19__create_business_audit_journal.sql" \
    "${expected_text}" \
    "migration V19"
done

readonly V20_TEXT=(
  "idx_business_audit_event_type_time"
  "idx_identity_security_event_time"
  "idx_outbox_replay_audit_time"
  "idx_settlement_resolution_time"
)

for expected_text in "${V20_TEXT[@]}"; do
  require_text \
    "backend/src/main/resources/db/migration/V20__add_normalized_audit_search_indexes.sql" \
    "${expected_text}" \
    "migration V20"
done

readonly V21_TEXT=(
  "idx_payment_created_report"
  "idx_settlement_import_completed_report"
  "idx_settlement_discrepancy_created_report"
  "idx_settlement_resolution_decision_report"
)

for expected_text in "${V21_TEXT[@]}"; do
  require_text \
    "backend/src/main/resources/db/migration/V21__add_operational_reporting_indexes.sql" \
    "${expected_text}" \
    "migration V21"
done

readonly AUDIT_SEARCH_FILTER_TEXT=(
  "Duration.ofDays(31)"
  "At least one audit search filter is required."
  "limit must be between 1 and 100."
)

for expected_text in "${AUDIT_SEARCH_FILTER_TEXT[@]}"; do
  require_text \
    "backend/src/main/java/com/samharrison/payments/reporting/internal/AuditSearchFilter.java" \
    "${expected_text}" \
    "audit search filter"
done

readonly AUDIT_SEARCH_SERVICE_TEXT=(
  "Isolation.REPEATABLE_READ"
  "ROLE_OPERATIONS"
  "ROLE_RECONCILIATION_ANALYST"
  "ROLE_ADMIN"
)

for expected_text in "${AUDIT_SEARCH_SERVICE_TEXT[@]}"; do
  require_text \
    "backend/src/main/java/com/samharrison/payments/reporting/internal/AuditSearchService.java" \
    "${expected_text}" \
    "audit search service"
done

readonly REPORT_EXPORT_TEXT=(
  "MAXIMUM_ROWS = 10_000"
  "Isolation.REPEATABLE_READ"
  "payments.csv"
  "settlements.csv"
  "reconciliation.csv"
)

for expected_text in "${REPORT_EXPORT_TEXT[@]}"; do
  require_text \
    "backend/src/main/java/com/samharrison/payments/reporting/internal/ReportExportService.java" \
    "${expected_text}" \
    "report export service"
done

readonly CSV_WRITER_TEXT=(
  "RECORD_SEPARATOR"
  '"\r\n"'
  "StandardCharsets.UTF_8"
)

for expected_text in "${CSV_WRITER_TEXT[@]}"; do
  require_text \
    "backend/src/main/java/com/samharrison/payments/reporting/internal/CsvDocumentWriter.java" \
    "${expected_text}" \
    "CSV document writer"
done

readonly REPORT_CONTROLLER_TEXT=(
  "CacheControl.noStore()"
  "HttpHeaders.CONTENT_DISPOSITION"
  "X-Content-Type-Options"
  "nosniff"
)

for expected_text in "${REPORT_CONTROLLER_TEXT[@]}"; do
  require_text \
    "backend/src/main/java/com/samharrison/payments/reporting/internal/ReportController.java" \
    "${expected_text}" \
    "report controller"
done

readonly REPORTING_WORKSPACE_TEXT=(
  "Maximum 31 days"
  "AuditSearchPanel"
  "OperationalSummaryPanel"
  "ReportDownloadsPanel"
)

for expected_text in "${REPORTING_WORKSPACE_TEXT[@]}"; do
  require_text \
    "frontend/src/features/reporting/components/AuditReportingWorkspace.tsx" \
    "${expected_text}" \
    "audit reporting workspace"
done

require_text \
  "frontend/src/features/identity/hooks/sessionCache.ts" \
  "'reporting'" \
  "session cache isolation"

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
  seq 1 21 |
    paste -sd, -
)"

readonly actual_versions="$(
  printf '%s\n' "${migration_versions[@]}" |
    paste -sd, -
)"

if [[ "${actual_versions}" != "${expected_versions}" ]]; then
  fail "expected exactly Flyway migrations 1 through 21, found: ${migration_versions[*]}"
fi

printf '\n==> Run Phase 9 cumulative baseline\n'
bash scripts/verify-phase-9.sh

printf '\n==> Check unstaged whitespace\n'
git diff --check

printf '\n==> Check staged whitespace\n'
git diff --cached --check

printf '\nPhase 10 verification passed.\n'
