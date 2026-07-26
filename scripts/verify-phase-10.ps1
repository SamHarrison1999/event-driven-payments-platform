[CmdletBinding()]
param()

$ErrorActionPreference = 'Stop'
Set-StrictMode -Version Latest

function Assert-RequiredFile {
  param(
    [Parameter(Mandatory = $true)]
    [string] $Path
  )

  if (-not (Test-Path -LiteralPath $Path -PathType Leaf)) {
    throw "Required file is missing: $Path"
  }

  if ((Get-Item -LiteralPath $Path).Length -eq 0) {
    throw "Required file is empty: $Path"
  }
}

function Assert-ContainsText {
  param(
    [Parameter(Mandatory = $true)]
    [string] $Path,

    [Parameter(Mandatory = $true)]
    [string[]] $ExpectedText,

    [Parameter(Mandatory = $true)]
    [string] $Description
  )

  $content = Get-Content `
    -LiteralPath $Path `
    -Raw `
    -Encoding UTF8

  foreach ($expected in $ExpectedText) {
    if ($content -notmatch [regex]::Escape($expected)) {
      throw (
        "$Description is missing required text: " +
        $expected
      )
    }
  }
}

function Invoke-CheckedCommand {
  param(
    [Parameter(Mandatory = $true)]
    [string] $Description,

    [Parameter(Mandatory = $true)]
    [scriptblock] $Command
  )

  Write-Host ''
  Write-Host "==> $Description"

  & $Command

  if ($LASTEXITCODE -ne 0) {
    throw "$Description failed with exit code $LASTEXITCODE."
  }
}

$repositoryRoot = Split-Path -Parent $PSScriptRoot

$requiredFiles = @(
  'README.md',
  'docs/architecture/overview.md',
  'docs/adr/0014-audit-and-operational-reporting.md',
  'docs/progress/ledger.md',

  'backend/src/main/resources/db/migration/V19__create_business_audit_journal.sql',
  'backend/src/main/resources/db/migration/V20__add_normalized_audit_search_indexes.sql',
  'backend/src/main/resources/db/migration/V21__add_operational_reporting_indexes.sql',

  'backend/src/main/java/com/samharrison/payments/audit/BusinessAuditEventRequest.java',
  'backend/src/main/java/com/samharrison/payments/audit/BusinessAuditEvents.java',
  'backend/src/main/java/com/samharrison/payments/audit/BusinessAuditRecorder.java',
  'backend/src/main/java/com/samharrison/payments/audit/BusinessAuditEvidenceReader.java',
  'backend/src/main/java/com/samharrison/payments/audit/internal/BusinessAuditMetadataSerializer.java',
  'backend/src/main/java/com/samharrison/payments/audit/internal/BusinessAuditRecorderService.java',

  'backend/src/main/java/com/samharrison/payments/identity/IdentitySecurityAuditReader.java',
  'backend/src/main/java/com/samharrison/payments/outbox/OutboxReplayAuditReader.java',
  'backend/src/main/java/com/samharrison/payments/reconciliation/SettlementResolutionAuditReader.java',
  'backend/src/main/java/com/samharrison/payments/payment/PaymentOperationalReportReader.java',
  'backend/src/main/java/com/samharrison/payments/reconciliation/OperationalReconciliationReportReader.java',

  'backend/src/main/java/com/samharrison/payments/reporting/internal/AuditCursorCodec.java',
  'backend/src/main/java/com/samharrison/payments/reporting/internal/AuditSearchFilter.java',
  'backend/src/main/java/com/samharrison/payments/reporting/internal/AuditSearchService.java',
  'backend/src/main/java/com/samharrison/payments/reporting/internal/AuditEventController.java',
  'backend/src/main/java/com/samharrison/payments/reporting/internal/ReportWindow.java',
  'backend/src/main/java/com/samharrison/payments/reporting/internal/CsvDocumentWriter.java',
  'backend/src/main/java/com/samharrison/payments/reporting/internal/OperationalSummaryService.java',
  'backend/src/main/java/com/samharrison/payments/reporting/internal/ReportExportService.java',
  'backend/src/main/java/com/samharrison/payments/reporting/internal/ReportController.java',

  'backend/src/test/java/com/samharrison/payments/audit/internal/BusinessAuditPersistenceIntegrationTest.java',
  'backend/src/test/java/com/samharrison/payments/reporting/internal/AuditSearchHttpIntegrationTest.java',
  'backend/src/test/java/com/samharrison/payments/reporting/internal/OperationalReportingHttpIntegrationTest.java',
  'backend/src/test/java/com/samharrison/payments/reporting/internal/CsvDocumentWriterTest.java',
  'backend/src/test/java/com/samharrison/payments/ModularityTest.java',

  'frontend/src/features/reporting/api/auditEvent.ts',
  'frontend/src/features/reporting/api/downloadReport.ts',
  'frontend/src/features/reporting/api/reportingApi.test.ts',
  'frontend/src/features/reporting/components/AuditReportingWorkspace.tsx',
  'frontend/src/features/reporting/components/AuditReportingWorkspace.test.tsx',
  'frontend/src/features/reporting/components/AuditSearchPanel.tsx',
  'frontend/src/features/reporting/components/OperationalSummaryPanel.tsx',
  'frontend/src/features/reporting/components/ReportDownloadsPanel.tsx',
  'frontend/src/features/reporting/hooks/useReportingSessionExpiry.ts',

  'scripts/verify-phase-9.ps1',
  'scripts/verify-phase-9.sh',
  'scripts/verify-phase-10.ps1',
  'scripts/verify-phase-10.sh'
)

Push-Location $repositoryRoot

try {
  Write-Host '==> Check required Phase 10 files'

  foreach ($file in $requiredFiles) {
    Assert-RequiredFile -Path $file
  }

  Write-Host ''
  Write-Host '==> Validate Phase 10 documentation'

  Assert-ContainsText `
    -Path 'README.md' `
    -Description 'README' `
    -ExpectedText @(
      'Audit and operational reporting',
      'GET /api/v1/audit-events',
      'GET /api/v1/reports/operational-summary',
      'GET /api/v1/reports/audit-events.csv',
      'GET /api/v1/reports/payments.csv',
      'GET /api/v1/reports/settlements.csv',
      'GET /api/v1/reports/reconciliation.csv',
      'Phase 10 verification',
      'verify-phase-10.ps1',
      'verify-phase-10.sh'
    )

  Assert-ContainsText `
    -Path 'docs/architecture/overview.md' `
    -Description 'Architecture overview' `
    -ExpectedText @(
      'Phase 10 implements',
      'Migration version 19 creates',
      'Migration version 20 adds',
      'Migration version 21 adds',
      'normalized read-only audit projections',
      'bounded, formula-safe CSV report exports',
      'Visibility is applied in the service query before pagination'
    )

  Assert-ContainsText `
    -Path 'docs/progress/ledger.md' `
    -Description 'Progress ledger' `
    -ExpectedText @(
      'Phase 10 acceptance criteria',
      'Canonical business-audit events are append-only | Completed',
      'Audit writes commit atomically with their business mutation | Completed',
      'Role visibility is enforced before paging and aggregation | Completed',
      'CSV uses fixed typed columns and RFC 4180 records | Completed',
      'Role-gated audit and reporting workspace is implemented | Completed',
      'PowerShell and Bash Phase 10 verifiers exist',
      'Cumulative Phase 10 verifier passes'
    )

  Write-Host ''
  Write-Host '==> Validate Phase 10 implementation contracts'

  Assert-ContainsText `
    -Path 'backend/src/main/java/com/samharrison/payments/audit/BusinessAuditEventRequest.java' `
    -Description 'Business audit request' `
    -ExpectedText @(
      'MAX_METADATA_ENTRIES = 16',
      'sourceEventIdentifier',
      'TreeMap',
      'METADATA_KEY_PATTERN'
    )

  Assert-ContainsText `
    -Path 'backend/src/main/java/com/samharrison/payments/audit/internal/BusinessAuditMetadataSerializer.java' `
    -Description 'Business audit metadata serializer' `
    -ExpectedText @(
      'MAX_METADATA_BYTES = 4_096',
      'writeValueAsString',
      'StandardCharsets.UTF_8'
    )

  Assert-ContainsText `
    -Path 'backend/src/main/resources/db/migration/V19__create_business_audit_journal.sql' `
    -Description 'Migration V19' `
    -ExpectedText @(
      'CREATE TABLE business_audit_event',
      'source_event_identifier VARCHAR(128) NOT NULL',
      'validate_business_audit_metadata',
      'trg_business_audit_event_immutable',
      'BEFORE UPDATE OR DELETE'
    )

  Assert-ContainsText `
    -Path 'backend/src/main/resources/db/migration/V20__add_normalized_audit_search_indexes.sql' `
    -Description 'Migration V20' `
    -ExpectedText @(
      'idx_business_audit_event_type_time',
      'idx_identity_security_event_time',
      'idx_outbox_replay_audit_time',
      'idx_settlement_resolution_time'
    )

  Assert-ContainsText `
    -Path 'backend/src/main/resources/db/migration/V21__add_operational_reporting_indexes.sql' `
    -Description 'Migration V21' `
    -ExpectedText @(
      'idx_payment_created_report',
      'idx_settlement_import_completed_report',
      'idx_settlement_discrepancy_created_report',
      'idx_settlement_resolution_decision_report'
    )

  Assert-ContainsText `
    -Path 'backend/src/main/java/com/samharrison/payments/reporting/internal/AuditSearchFilter.java' `
    -Description 'Audit search filter' `
    -ExpectedText @(
      'Duration.ofDays(31)',
      'At least one audit search filter is required.',
      'limit must be between 1 and 100.'
    )

  Assert-ContainsText `
    -Path 'backend/src/main/java/com/samharrison/payments/reporting/internal/AuditSearchService.java' `
    -Description 'Audit search service' `
    -ExpectedText @(
      'Isolation.REPEATABLE_READ',
      'ROLE_OPERATIONS',
      'ROLE_RECONCILIATION_ANALYST',
      'ROLE_ADMIN'
    )

  Assert-ContainsText `
    -Path 'backend/src/main/java/com/samharrison/payments/reporting/internal/ReportExportService.java' `
    -Description 'Report export service' `
    -ExpectedText @(
      'MAXIMUM_ROWS = 10_000',
      'Isolation.REPEATABLE_READ',
      'payments.csv',
      'settlements.csv',
      'reconciliation.csv'
    )

  Assert-ContainsText `
    -Path 'backend/src/main/java/com/samharrison/payments/reporting/internal/CsvDocumentWriter.java' `
    -Description 'CSV document writer' `
    -ExpectedText @(
      'RECORD_SEPARATOR',
      '"\r\n"',
      'StandardCharsets.UTF_8'
    )

  Assert-ContainsText `
    -Path 'backend/src/main/java/com/samharrison/payments/reporting/internal/ReportController.java' `
    -Description 'Report controller' `
    -ExpectedText @(
      'CacheControl.noStore()',
      'HttpHeaders.CONTENT_DISPOSITION',
      'X-Content-Type-Options',
      'nosniff'
    )

  Assert-ContainsText `
    -Path 'frontend/src/features/reporting/components/AuditReportingWorkspace.tsx' `
    -Description 'Audit reporting workspace' `
    -ExpectedText @(
      'Maximum 31 days',
      'AuditSearchPanel',
      'OperationalSummaryPanel',
      'ReportDownloadsPanel'
    )

  Assert-ContainsText `
    -Path 'frontend/src/features/identity/hooks/sessionCache.ts' `
    -Description 'Session cache isolation' `
    -ExpectedText @(
      "'reporting'"
    )

  Write-Host ''
  Write-Host '==> Validate Flyway migration sequence'

  $migrationVersions = @(
    Get-ChildItem `
      -LiteralPath 'backend/src/main/resources/db/migration' `
      -Filter 'V*__*.sql' |
    ForEach-Object {
      if ($_.Name -match '^V([0-9]+)__') {
        [int] $Matches[1]
      }
    } |
    Sort-Object
  )

  $expectedMigrationVersions = @(1..21)

  if (
    ($migrationVersions -join ',') -ne
      ($expectedMigrationVersions -join ',')
  ) {
    throw (
      'Expected exactly Flyway migrations 1 through 21, found: ' +
      ($migrationVersions -join ', ')
    )
  }

  Write-Host ''
  Write-Host '==> Run Phase 9 cumulative baseline'

  & (Join-Path $PSScriptRoot 'verify-phase-9.ps1')

  if (-not $?) {
    throw 'Phase 9 cumulative baseline failed.'
  }

  Invoke-CheckedCommand `
    -Description 'Check unstaged whitespace' `
    -Command {
      git diff --check
    }

  Invoke-CheckedCommand `
    -Description 'Check staged whitespace' `
    -Command {
      git diff --cached --check
    }

  Write-Host ''
  Write-Host 'Phase 10 verification passed.'
}
finally {
  Pop-Location
}
