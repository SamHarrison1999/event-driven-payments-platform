[CmdletBinding()]
param()

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

$repositoryRoot = Split-Path -Parent $PSScriptRoot

$requiredFiles = @(
  'README.md',
  'docs/architecture/overview.md',
  'docs/adr/0008-gbp-minor-units-and-ledger-entry-model.md',
  'docs/progress/ledger.md',

  'backend/src/main/resources/db/migration/V9__create_double_entry_ledger_schema.sql',
  'backend/src/main/resources/db/migration/V10__enforce_ledger_invariants.sql',

  'backend/src/main/java/com/samharrison/payments/ledger/LedgerPostingService.java',
  'backend/src/main/java/com/samharrison/payments/ledger/LedgerQueryService.java',
  'backend/src/main/java/com/samharrison/payments/ledger/LedgerPostingCommand.java',
  'backend/src/main/java/com/samharrison/payments/ledger/LedgerPostingEntry.java',
  'backend/src/main/java/com/samharrison/payments/ledger/PostedLedgerTransaction.java',
  'backend/src/main/java/com/samharrison/payments/ledger/LedgerBalanceVerification.java',
  'backend/src/main/java/com/samharrison/payments/ledger/internal/LedgerTransaction.java',
  'backend/src/main/java/com/samharrison/payments/ledger/internal/LedgerPersistenceStore.java',

  'backend/src/test/java/com/samharrison/payments/ledger/internal/LedgerTransactionTest.java',
  'backend/src/test/java/com/samharrison/payments/ledger/internal/LedgerPersistenceIntegrationTest.java',
  'backend/src/test/java/com/samharrison/payments/ledger/LedgerPostingServiceIntegrationTest.java',
  'backend/src/test/java/com/samharrison/payments/ledger/internal/LedgerDatabaseInvariantIntegrationTest.java',
  'backend/src/test/java/com/samharrison/payments/ledger/LedgerQueryServiceIntegrationTest.java',

  'scripts/verify-phase-3.ps1',
  'scripts/verify-phase-3.sh',
  'scripts/verify-phase-4.ps1',
  'scripts/verify-phase-4.sh'
)

Push-Location $repositoryRoot

try {
  Write-Host '==> Check required Phase 4 files'

  foreach ($file in $requiredFiles) {
    if (-not (Test-Path -LiteralPath $file -PathType Leaf)) {
      throw "Required file is missing: $file"
    }

    if ((Get-Item -LiteralPath $file).Length -eq 0) {
      throw "Required file is empty: $file"
    }
  }

  Write-Host ''
  Write-Host '==> Validate Phase 4 documentation'

  $readme = Get-Content `
    -LiteralPath 'README.md' `
    -Raw `
    -Encoding UTF8

  $requiredReadmeText = @(
    'Phase 4',
    'Double-entry ledger',
    'verify-phase-4.ps1',
    'explicit `DEBIT` and `CREDIT`',
    'deferred PostgreSQL balance verification',
    'verification of account snapshots against ledger debit and credit totals',
    'this application does not process real money'
  )

  foreach ($expectedText in $requiredReadmeText) {
    if ($readme -notmatch [regex]::Escape($expectedText)) {
      throw "README is missing required text: $expectedText"
    }
  }

  $architecture = Get-Content `
    -LiteralPath 'docs/architecture/overview.md' `
    -Raw `
    -Encoding UTF8

  $requiredArchitectureText = @(
    'Phase 4',
    'Ledger posting',
    'Migration version 9',
    'Migration version 10',
    'deferred PostgreSQL balance constraint',
    'Posted headers and entries cannot be updated or deleted',
    'Ledger-derived account totals'
  )

  foreach ($expectedText in $requiredArchitectureText) {
    if (
      $architecture -notmatch
        [regex]::Escape($expectedText)
    ) {
      throw (
        'Architecture overview is missing required text: ' +
        $expectedText
      )
    }
  }

  $ledger = Get-Content `
    -LiteralPath 'docs/progress/ledger.md' `
    -Raw `
    -Encoding UTF8

  $requiredLedgerText = @(
    'Phase 4 acceptance evidence',
    'Double-entry ledger | Completed',
    'Ledger domain and posting',
    'PostgreSQL integrity and immutability',
    'Queries, verification and architecture',
    'Focused Phase 4 ledger suite passes'
  )

  foreach ($expectedText in $requiredLedgerText) {
    if ($ledger -notmatch [regex]::Escape($expectedText)) {
      throw "Progress ledger is missing required text: $expectedText"
    }
  }

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

  $expectedMigrationVersions = @(1..10)
  $phase4MigrationVersions = @(
    $migrationVersions |
      Select-Object -First 10
  )

  if (
    ($phase4MigrationVersions -join ',') -ne
      ($expectedMigrationVersions -join ',')
  ) {
    throw (
      'Expected Flyway migrations 1 through 10 to remain present, found: ' +
      ($migrationVersions -join ', ')
    )
  }
  Write-Host ''
  Write-Host '==> Run Phase 3 baseline verification'

  & (Join-Path $PSScriptRoot 'verify-phase-3.ps1')

  Write-Host ''
  Write-Host 'Phase 4 verification passed.'
}
finally {
  Pop-Location
}
