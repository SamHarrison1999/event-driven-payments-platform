[CmdletBinding()]
param()

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

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
  'docs/adr/0005-transactional-outbox.md',
  'docs/adr/0011-asynchronous-events-and-outbox.md',
  'docs/progress/ledger.md',

  'backend/src/main/resources/db/migration/V13__create_transactional_outbox.sql',

  'backend/src/main/java/com/samharrison/payments/outbox/OutboxEventAppender.java',
  'backend/src/main/java/com/samharrison/payments/outbox/OutboxEventRequest.java',
  'backend/src/main/java/com/samharrison/payments/outbox/package-info.java',
  'backend/src/main/java/com/samharrison/payments/outbox/internal/OutboxEvent.java',
  'backend/src/main/java/com/samharrison/payments/outbox/internal/OutboxEventRepository.java',
  'backend/src/main/java/com/samharrison/payments/outbox/internal/OutboxClaimingService.java',
  'backend/src/main/java/com/samharrison/payments/outbox/internal/OutboxPublicationFinalizer.java',
  'backend/src/main/java/com/samharrison/payments/outbox/internal/OutboxPublisher.java',
  'backend/src/main/java/com/samharrison/payments/outbox/internal/LoggingOutboxTransport.java',

  'backend/src/main/java/com/samharrison/payments/payment/internal/PaymentCompletedOutboxEventFactory.java',
  'backend/src/main/java/com/samharrison/payments/payment/internal/PaymentPostingTransaction.java',

  'backend/src/test/java/com/samharrison/payments/outbox/OutboxEventRequestTest.java',
  'backend/src/test/java/com/samharrison/payments/outbox/internal/OutboxEventTest.java',
  'backend/src/test/java/com/samharrison/payments/outbox/internal/OutboxPublicationIntegrationTest.java',
  'backend/src/test/java/com/samharrison/payments/payment/internal/PaymentCompletedOutboxEventFactoryTest.java',
  'backend/src/test/java/com/samharrison/payments/payment/internal/PaymentProcessingIntegrationTest.java',
  'backend/src/test/java/com/samharrison/payments/ModularityTest.java',

  'scripts/verify-phase-6.ps1',
  'scripts/verify-phase-6.sh',
  'scripts/verify-phase-7.ps1',
  'scripts/verify-phase-7.sh'
)

Push-Location $repositoryRoot

try {
  Write-Host '==> Check required Phase 7 files'

  foreach ($file in $requiredFiles) {
    if (-not (Test-Path -LiteralPath $file -PathType Leaf)) {
      throw "Required file is missing: $file"
    }

    if ((Get-Item -LiteralPath $file).Length -eq 0) {
      throw "Required file is empty: $file"
    }
  }

  Write-Host ''
  Write-Host '==> Validate Phase 7 documentation'

  $readme = Get-Content `
    -LiteralPath 'README.md' `
    -Raw `
    -Encoding UTF8

  $requiredReadmeText = @(
    'Transactional outbox',
    'payment.completed.v1',
    'FOR UPDATE SKIP LOCKED',
    'Phase 7 verification',
    'verify-phase-7.ps1',
    'verify-phase-7.sh'
  )

  foreach ($expectedText in $requiredReadmeText) {
    if ($readme -notmatch [regex]::Escape($expectedText)) {
      throw "README is missing required text: $expectedText"
    }
  }

  $adr = Get-Content `
    -LiteralPath 'docs/adr/0011-asynchronous-events-and-outbox.md' `
    -Raw `
    -Encoding UTF8

  $requiredAdrText = @(
    'payment.completed.v1',
    'same PostgreSQL transaction',
    'SKIP LOCKED',
    'PUBLISHING',
    'PUBLISHED',
    'DEAD_LETTER'
  )

  foreach ($expectedText in $requiredAdrText) {
    if ($adr -notmatch [regex]::Escape($expectedText)) {
      throw "ADR 0011 is missing required text: $expectedText"
    }
  }

  $architecture = Get-Content `
    -LiteralPath 'docs/architecture/overview.md' `
    -Raw `
    -Encoding UTF8

  $requiredArchitectureText = @(
    'Asynchronous events and outbox',
    'payment.completed.v1',
    'Migration version 13',
    'owner-token publication lease',
    'outbox event commit atomically'
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
    'Phase 7 acceptance evidence',
    'Completed payments create one outbox event atomically',
    'Bounded event claiming uses owner tokens and leases',
    'PowerShell and Bash Phase 7 verifiers exist',
    'Composite Phase 7 verifier passes'
  )

  foreach ($expectedText in $requiredLedgerText) {
    if ($ledger -notmatch [regex]::Escape($expectedText)) {
      throw "Progress ledger is missing required text: $expectedText"
    }
  }

  Write-Host ''
  Write-Host '==> Validate Phase 7 implementation contracts'

  $migration = Get-Content `
    -LiteralPath 'backend/src/main/resources/db/migration/V13__create_transactional_outbox.sql' `
    -Raw `
    -Encoding UTF8

  $requiredMigrationText = @(
    'CREATE TABLE outbox_event',
    'PENDING',
    'PUBLISHING',
    'PUBLISHED',
    'DEAD_LETTER',
    'payload IS JSON OBJECT',
    'publication_owner_token',
    'publication_lease_expires_at'
  )

  foreach ($expectedText in $requiredMigrationText) {
    if ($migration -notmatch [regex]::Escape($expectedText)) {
      throw "Migration V13 is missing required text: $expectedText"
    }
  }

  $repository = Get-Content `
    -LiteralPath 'backend/src/main/java/com/samharrison/payments/outbox/internal/OutboxEventRepository.java' `
    -Raw `
    -Encoding UTF8

  if (
    $repository -notmatch
      [regex]::Escape('FOR UPDATE SKIP LOCKED')
  ) {
    throw 'Outbox repository is missing SKIP LOCKED claiming.'
  }

  $factory = Get-Content `
    -LiteralPath 'backend/src/main/java/com/samharrison/payments/payment/internal/PaymentCompletedOutboxEventFactory.java' `
    -Raw `
    -Encoding UTF8

  $requiredFactoryText = @(
    'payment.completed.v1',
    'amountMinorUnits',
    'ledgerTransactionId',
    'completedAt'
  )

  foreach ($expectedText in $requiredFactoryText) {
    if ($factory -notmatch [regex]::Escape($expectedText)) {
      throw "Payment event factory is missing required text: $expectedText"
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

  $expectedMigrationVersions = @(1..13)
  $phase7MigrationVersions = @(
    $migrationVersions |
      Select-Object -First 13
  )

  if (
    ($phase7MigrationVersions -join ',') -ne
      ($expectedMigrationVersions -join ',')
  ) {
    throw (
      'Expected Flyway migrations 1 through 13 to remain present, found: ' +
      ($migrationVersions -join ', ')
    )
  }

  Write-Host ''
  Write-Host '==> Run Phase 6 baseline verification'

  & (Join-Path $PSScriptRoot 'verify-phase-6.ps1')

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
  Write-Host 'Phase 7 verification passed.'
}
finally {
  Pop-Location
}
