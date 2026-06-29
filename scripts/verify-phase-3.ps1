[CmdletBinding()]
param()

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

$repositoryRoot = Split-Path -Parent $PSScriptRoot

$requiredFiles = @(
  'README.md',
  'docs/architecture/overview.md',
  'docs/progress/ledger.md',

  'backend/src/main/resources/db/migration/V5__create_customer_schema.sql',
  'backend/src/main/resources/db/migration/V6__create_account_schema.sql',
  'backend/src/main/resources/db/migration/V7__create_customer_identity_assignment.sql',
  'backend/src/main/resources/db/migration/V8__harden_phase_3_validation.sql',

  'backend/src/main/java/com/samharrison/payments/shared/GbpAmount.java',
  'backend/src/main/java/com/samharrison/payments/customer/internal/CustomerProfile.java',
  'backend/src/main/java/com/samharrison/payments/customer/internal/CustomerManagementController.java',
  'backend/src/main/java/com/samharrison/payments/customer/internal/CustomerOwnershipManagementController.java',
  'backend/src/main/java/com/samharrison/payments/account/internal/CustomerAccount.java',
  'backend/src/main/java/com/samharrison/payments/account/internal/AccountManagementController.java',
  'backend/src/main/java/com/samharrison/payments/account/internal/CustomerAccountQueryController.java',
  'backend/src/main/java/com/samharrison/payments/identity/internal/IdentitySecurityProblemHandler.java',

  'backend/src/test/java/com/samharrison/payments/customer/internal/CustomerPersistenceIntegrationTest.java',
  'backend/src/test/java/com/samharrison/payments/customer/internal/CustomerConditionalUpdateHttpIntegrationTest.java',
  'backend/src/test/java/com/samharrison/payments/account/internal/CustomerAccountPersistenceIntegrationTest.java',
  'backend/src/test/java/com/samharrison/payments/account/internal/CustomerAccountOwnershipHttpIntegrationTest.java',
  'backend/src/test/java/com/samharrison/payments/account/internal/AccountConditionalUpdateHttpIntegrationTest.java',

  'scripts/verify-phase-2.ps1',
  'scripts/verify-phase-2.sh',
  'scripts/verify-phase-3.ps1',
  'scripts/verify-phase-3.sh'
)

Push-Location $repositoryRoot

try {
  Write-Host '==> Check required Phase 3 files'

  foreach ($file in $requiredFiles) {
    if (-not (Test-Path -LiteralPath $file -PathType Leaf)) {
      throw "Required file is missing: $file"
    }

    if ((Get-Item -LiteralPath $file).Length -eq 0) {
      throw "Required file is empty: $file"
    }
  }

  Write-Host ''
  Write-Host '==> Validate Phase 3 documentation'

  $readme = Get-Content `
    -LiteralPath 'README.md' `
    -Raw `
    -Encoding UTF8

  $requiredReadmeText = @(
    'Phase 3',
    'Customers and accounts',
    'verify-phase-3.ps1',
    'POST /api/v1/customers',
    'POST /api/v1/accounts',
    'If-Match',
    'application/problem+json',
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
    'Phase 3',
    'Customer and account lifecycle',
    'optimistic concurrency',
    'Migration version 8',
    'structured `401 Unauthorized`',
    'Stale writes return `412 Precondition Failed`'
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
    'Phase 3 acceptance evidence',
    'Customers and accounts | Current',
    'Customer profiles and ownership',
    'GBP accounts and customer views',
    'Validation, security and verification',
    'Full backend test and JAR gate passes'
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

  $expectedMigrationVersions = @(1..8)

  if (
    ($migrationVersions -join ',') -ne
      ($expectedMigrationVersions -join ',')
  ) {
    throw (
      'Expected Flyway migrations 1 through 8 exactly, found: ' +
      ($migrationVersions -join ', ')
    )
  }

  Write-Host ''
  Write-Host '==> Run Phase 2 baseline verification'

  & (Join-Path $PSScriptRoot 'verify-phase-2.ps1')

  Write-Host ''
  Write-Host 'Phase 3 verification passed.'
}
finally {
  Pop-Location
}