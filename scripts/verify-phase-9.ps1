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
  'docs/adr/0013-settlement-and-reconciliation.md',
  'docs/progress/ledger.md',

  'backend/src/main/resources/db/migration/V16__create_settlement_import_foundation.sql',
  'backend/src/main/resources/db/migration/V17__create_settlement_reconciliation_results.sql',
  'backend/src/main/resources/db/migration/V18__create_settlement_resolution_evidence.sql',

  'backend/src/main/java/com/samharrison/payments/payment/PaymentReconciliationReader.java',
  'backend/src/main/java/com/samharrison/payments/payment/PaymentReconciliationSnapshot.java',
  'backend/src/main/java/com/samharrison/payments/payment/internal/PaymentReconciliationReaderService.java',

  'backend/src/main/java/com/samharrison/payments/reconciliation/internal/SettlementCsvParser.java',
  'backend/src/main/java/com/samharrison/payments/reconciliation/internal/SettlementImportService.java',
  'backend/src/main/java/com/samharrison/payments/reconciliation/internal/SettlementImportTransaction.java',
  'backend/src/main/java/com/samharrison/payments/reconciliation/internal/SettlementMatcher.java',
  'backend/src/main/java/com/samharrison/payments/reconciliation/internal/SettlementImportController.java',
  'backend/src/main/java/com/samharrison/payments/reconciliation/internal/SettlementDiscrepancyController.java',
  'backend/src/main/java/com/samharrison/payments/reconciliation/internal/SettlementDiscrepancyResolutionService.java',
  'backend/src/main/java/com/samharrison/payments/reconciliation/internal/SettlementResolution.java',

  'backend/src/test/java/com/samharrison/payments/reconciliation/internal/SettlementCsvParserTest.java',
  'backend/src/test/java/com/samharrison/payments/reconciliation/internal/SettlementMatcherTest.java',
  'backend/src/test/java/com/samharrison/payments/reconciliation/internal/SettlementPersistenceIntegrationTest.java',
  'backend/src/test/java/com/samharrison/payments/reconciliation/internal/SettlementImportWorkflowIntegrationTest.java',
  'backend/src/test/java/com/samharrison/payments/reconciliation/internal/SettlementImportHttpIntegrationTest.java',
  'backend/src/test/java/com/samharrison/payments/reconciliation/internal/SettlementDiscrepancyPersistenceIntegrationTest.java',
  'backend/src/test/java/com/samharrison/payments/reconciliation/internal/SettlementDiscrepancyHttpIntegrationTest.java',
  'backend/src/test/java/com/samharrison/payments/ModularityTest.java',

  'frontend/src/features/reconciliation/api/settlement.ts',
  'frontend/src/features/reconciliation/api/uploadSettlementFile.ts',
  'frontend/src/features/reconciliation/api/resolveSettlementDiscrepancy.ts',
  'frontend/src/features/reconciliation/api/settlementApi.test.ts',
  'frontend/src/features/reconciliation/components/ReconciliationWorkspace.tsx',
  'frontend/src/features/reconciliation/components/ReconciliationWorkspace.test.tsx',
  'frontend/src/features/reconciliation/components/SettlementImportPanel.tsx',
  'frontend/src/features/reconciliation/components/SettlementDiscrepancyPanel.tsx',

  'scripts/verify-phase-8.ps1',
  'scripts/verify-phase-8.sh',
  'scripts/verify-phase-9.ps1',
  'scripts/verify-phase-9.sh'
)

Push-Location $repositoryRoot

try {
  Write-Host '==> Check required Phase 9 files'

  foreach ($file in $requiredFiles) {
    Assert-RequiredFile -Path $file
  }

  Write-Host ''
  Write-Host '==> Validate Phase 9 documentation'

  Assert-ContainsText `
    -Path 'README.md' `
    -Description 'README' `
    -ExpectedText @(
      'Settlement and reconciliation',
      'POST /api/v1/settlement-imports',
      'GET  /api/v1/settlement-discrepancies',
      'PUT  /api/v1/settlement-discrepancies/{discrepancyId}/resolution',
      'Phase 9 verification',
      'verify-phase-9.ps1',
      'verify-phase-9.sh'
    )

  Assert-ContainsText `
    -Path 'docs/architecture/overview.md' `
    -Description 'Architecture overview' `
    -ExpectedText @(
      'Phase 9 implements',
      'Migration version 16',
      'Migration version 17',
      'Migration version 18',
      'database-protected accepted settlement match',
      'one-time discrepancy resolution',
      'Payment processing and settlement reconciliation are separate state machines'
    )

  Assert-ContainsText `
    -Path 'docs/progress/ledger.md' `
    -Description 'Progress ledger' `
    -ExpectedText @(
      'Phase 9 acceptance evidence',
      'Exact bounded CSV contract is implemented | Completed',
      'Import, rows, results and discrepancies commit atomically | Completed',
      'Discrepancy resolution uses strong ETags and `If-Match` | Completed',
      'React analyst workflow passes frontend tests | Completed',
      'Phase 9 PowerShell and Bash verifiers exist',
      'Cumulative Phase 9 verifier passes'
    )

  Write-Host ''
  Write-Host '==> Validate Phase 9 implementation contracts'

  Assert-ContainsText `
    -Path 'backend/src/main/java/com/samharrison/payments/reconciliation/internal/SettlementCsvParser.java' `
    -Description 'Settlement CSV parser' `
    -ExpectedText @(
      'MAX_FILE_SIZE_BYTES = 1_048_576',
      'MAX_DATA_ROWS = 1_000',
      'UTF8_BOM_NOT_ALLOWED',
      'MALFORMED_UTF8',
      'MessageDigest',
      '"SHA-256"'
    )

  Assert-ContainsText `
    -Path 'backend/src/main/java/com/samharrison/payments/payment/PaymentReconciliationReader.java' `
    -Description 'Payment reconciliation reader' `
    -ExpectedText @(
      'MAX_PAYMENT_IDS = 1_000',
      'PaymentReconciliationSnapshot'
    )

  Assert-ContainsText `
    -Path 'backend/src/main/java/com/samharrison/payments/reconciliation/internal/SettlementMatcher.java' `
    -Description 'Settlement matcher' `
    -ExpectedText @(
      'PAYMENT_NOT_FOUND',
      'PAYMENT_NOT_COMPLETED',
      'CURRENCY_MISMATCH',
      'AMOUNT_MISMATCH',
      'SETTLED_BEFORE_COMPLETION'
    )

  Assert-ContainsText `
    -Path 'backend/src/main/java/com/samharrison/payments/reconciliation/internal/SettlementImportTransaction.java' `
    -Description 'Settlement import transaction' `
    -ExpectedText @(
      'DUPLICATE_PAYMENT_SETTLEMENT'
    )

  Assert-ContainsText `
    -Path 'backend/src/main/resources/db/migration/V16__create_settlement_import_foundation.sql' `
    -Description 'Migration V16' `
    -ExpectedText @(
      'CREATE TABLE settlement_import',
      'CREATE TABLE settlement_record',
      'UNIQUE (raw_file_sha256)',
      'trg_settlement_record_immutable',
      'BEFORE UPDATE OR DELETE'
    )

  Assert-ContainsText `
    -Path 'backend/src/main/resources/db/migration/V17__create_settlement_reconciliation_results.sql' `
    -Description 'Migration V17' `
    -ExpectedText @(
      'CREATE TABLE settlement_result',
      'CREATE TABLE settlement_match_claim',
      'CREATE TABLE settlement_discrepancy',
      'trg_settlement_result_immutable',
      'trg_settlement_claim_immutable'
    )

  Assert-ContainsText `
    -Path 'backend/src/main/resources/db/migration/V18__create_settlement_resolution_evidence.sql' `
    -Description 'Migration V18' `
    -ExpectedText @(
      'CREATE TABLE settlement_resolution',
      'trg_settlement_resolution_immutable',
      'trg_settlement_discrepancy_lifecycle',
      'CREATE CONSTRAINT TRIGGER'
    )

  Assert-ContainsText `
    -Path 'backend/src/main/java/com/samharrison/payments/reconciliation/internal/SettlementDiscrepancyResolutionService.java' `
    -Description 'Discrepancy resolution service' `
    -ExpectedText @(
      'RECONCILIATION_ANALYST',
      'ADMIN',
      'SettlementResolution'
    )

  Assert-ContainsText `
    -Path 'frontend/src/features/reconciliation/api/resolveSettlementDiscrepancy.ts' `
    -Description 'Frontend resolution API' `
    -ExpectedText @(
      'getCsrfHeaders',
      "csrfHeaders.set('If-Match'",
      'expectedStatus: 200'
    )

  Assert-ContainsText `
    -Path 'frontend/src/features/reconciliation/components/SettlementImportPanel.tsx' `
    -Description 'Settlement import panel' `
    -ExpectedText @(
      'UTF-8 CSV, 1–1,000 rows, maximum 1 MiB.',
      'Raw SHA-256'
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

  $expectedMigrationVersions = @(1..18)

  if (
    ($migrationVersions -join ',') -ne
      ($expectedMigrationVersions -join ',')
  ) {
    throw (
      'Expected exactly Flyway migrations 1 through 18, found: ' +
      ($migrationVersions -join ', ')
    )
  }

  Write-Host ''
  Write-Host '==> Run Phase 8 cumulative baseline'

  & (Join-Path $PSScriptRoot 'verify-phase-8.ps1')

  if (-not $?) {
    throw 'Phase 8 cumulative baseline failed.'
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
  Write-Host 'Phase 9 verification passed.'
}
finally {
  Pop-Location
}
