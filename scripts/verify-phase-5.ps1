[CmdletBinding()]
param()

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

$repositoryRoot = Split-Path -Parent $PSScriptRoot

$requiredFiles = @(
  'README.md',
  'docs/architecture/overview.md',
  'docs/adr/0006-idempotency-and-payment-lifecycle.md',
  'docs/adr/0009-synchronous-payment-orchestration.md',
  'docs/progress/ledger.md',

  'backend/src/main/resources/db/migration/V11__create_payment_and_idempotency_schema.sql',
  'backend/src/main/resources/db/migration/V12__allow_unknown_payment_account_references.sql',

  'backend/src/main/java/com/samharrison/payments/customer/CustomerOwnership.java',
  'backend/src/main/java/com/samharrison/payments/account/AccountPaymentMutation.java',
  'backend/src/main/java/com/samharrison/payments/account/AccountPaymentResult.java',
  'backend/src/main/java/com/samharrison/payments/account/internal/AccountPaymentMutationService.java',

  'backend/src/main/java/com/samharrison/payments/payment/internal/Payment.java',
  'backend/src/main/java/com/samharrison/payments/payment/internal/PaymentIdempotencyRecord.java',
  'backend/src/main/java/com/samharrison/payments/payment/internal/PaymentReservationCoordinator.java',
  'backend/src/main/java/com/samharrison/payments/payment/internal/PaymentPostingTransaction.java',
  'backend/src/main/java/com/samharrison/payments/payment/internal/PaymentProcessingCoordinator.java',
  'backend/src/main/java/com/samharrison/payments/payment/internal/PaymentSubmissionService.java',
  'backend/src/main/java/com/samharrison/payments/payment/internal/PaymentQueryService.java',
  'backend/src/main/java/com/samharrison/payments/payment/internal/PaymentController.java',

  'backend/src/test/java/com/samharrison/payments/account/internal/AccountPaymentMutationServiceIntegrationTest.java',
  'backend/src/test/java/com/samharrison/payments/payment/internal/PaymentTest.java',
  'backend/src/test/java/com/samharrison/payments/payment/internal/PaymentPersistenceIntegrationTest.java',
  'backend/src/test/java/com/samharrison/payments/payment/internal/PaymentReservationCoordinatorIntegrationTest.java',
  'backend/src/test/java/com/samharrison/payments/payment/internal/PaymentProcessingIntegrationTest.java',
  'backend/src/test/java/com/samharrison/payments/payment/internal/PaymentSubmissionHttpIntegrationTest.java',
  'backend/src/test/java/com/samharrison/payments/payment/internal/PaymentQueryHttpIntegrationTest.java',
  'backend/src/test/java/com/samharrison/payments/ModularityTest.java',

  'scripts/verify-phase-4.ps1',
  'scripts/verify-phase-4.sh',
  'scripts/verify-phase-5.ps1',
  'scripts/verify-phase-5.sh'
)

Push-Location $repositoryRoot

try {
  Write-Host '==> Check required Phase 5 files'

  foreach ($file in $requiredFiles) {
    if (-not (Test-Path -LiteralPath $file -PathType Leaf)) {
      throw "Required file is missing: $file"
    }

    if ((Get-Item -LiteralPath $file).Length -eq 0) {
      throw "Required file is empty: $file"
    }
  }

  Write-Host ''
  Write-Host '==> Validate Phase 5 documentation'

  $readme = Get-Content `
    -LiteralPath 'README.md' `
    -Raw `
    -Encoding UTF8

  $requiredReadmeText = @(
    'Synchronous payments',
    'POST /api/v1/payments',
    'GET  /api/v1/payments/{paymentId}',
    'Idempotency-Key',
    'verify-phase-5.ps1',
    'does not process real money'
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
    'Synchronous payments',
    'durable idempotency reservation',
    'Migration version 11',
    'Migration version 12',
    'Payment reservation',
    'Core payment posting',
    'PAYMENT_INSUFFICIENT_FUNDS',
    'Phase 5 does not create outbox or business-audit records'
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
    'Phase 5 acceptance evidence',
    'Synchronous payments | Completed',
    'Domain, persistence and idempotency',
    'Account mutation and atomic processing',
    'HTTP, security and verification',
    'Complete backend regression passes'
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

  $expectedMigrationVersions = @(1..12)
  $phase5MigrationVersions = @(
    $migrationVersions |
      Select-Object -First 12
  )

  if (
    ($phase5MigrationVersions -join ',') -ne
      ($expectedMigrationVersions -join ',')
  ) {
    throw (
      'Expected Flyway migrations 1 through 12 to remain present, found: ' +
      ($migrationVersions -join ', ')
    )
  }

  Write-Host ''
  Write-Host '==> Run Phase 4 baseline verification'

  & (Join-Path $PSScriptRoot 'verify-phase-4.ps1')

  Write-Host ''
  Write-Host 'Phase 5 verification passed.'
}
finally {
  Pop-Location
}
