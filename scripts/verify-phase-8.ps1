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
  'docs/adr/0012-notifications-and-dead-letter-operations.md',
  'docs/progress/ledger.md',

  'backend/src/main/resources/db/migration/V14__create_notification_consumer.sql',
  'backend/src/main/resources/db/migration/V15__add_outbox_dead_letter_replay.sql',

  'backend/src/main/java/com/samharrison/payments/outbox/PublishedOutboxEventReader.java',
  'backend/src/main/java/com/samharrison/payments/outbox/internal/PublishedOutboxEventReaderService.java',
  'backend/src/main/java/com/samharrison/payments/outbox/internal/OutboxDeadLetterOperationsService.java',
  'backend/src/main/java/com/samharrison/payments/outbox/internal/OutboxReplayAudit.java',

  'backend/src/main/java/com/samharrison/payments/notification/internal/NotificationEventConsumer.java',
  'backend/src/main/java/com/samharrison/payments/notification/internal/NotificationClaimingService.java',
  'backend/src/main/java/com/samharrison/payments/notification/internal/NotificationDeliveryProcessor.java',
  'backend/src/main/java/com/samharrison/payments/notification/internal/NotificationQueryController.java',
  'backend/src/main/java/com/samharrison/payments/notification/internal/OutboxDeadLetterController.java',

  'backend/src/test/java/com/samharrison/payments/notification/internal/NotificationEventConsumerIntegrationTest.java',
  'backend/src/test/java/com/samharrison/payments/notification/internal/NotificationDeliveryIntegrationTest.java',
  'backend/src/test/java/com/samharrison/payments/notification/internal/NotificationOperationsIntegrationTest.java',
  'backend/src/test/java/com/samharrison/payments/notification/internal/NotificationHttpIntegrationTest.java',
  'backend/src/test/java/com/samharrison/payments/ModularityTest.java',

  'frontend/src/features/notifications/components/NotificationPanel.tsx',
  'frontend/src/features/notifications/components/NotificationPanel.test.tsx',
  'frontend/src/features/operations/components/DeadLetterPanel.tsx',
  'frontend/src/features/operations/components/DeadLetterPanel.test.tsx',
  'frontend/src/features/operations/api/replayDeadLetter.ts',
  'frontend/src/features/identity/hooks/sessionCache.ts',

  'scripts/verify-phase-7.ps1',
  'scripts/verify-phase-7.sh',
  'scripts/verify-phase-8.ps1',
  'scripts/verify-phase-8.sh'
)

Push-Location $repositoryRoot

try {
  Write-Host '==> Check required Phase 8 files'

  foreach ($file in $requiredFiles) {
    if (-not (Test-Path -LiteralPath $file -PathType Leaf)) {
      throw "Required file is missing: $file"
    }

    if ((Get-Item -LiteralPath $file).Length -eq 0) {
      throw "Required file is empty: $file"
    }
  }

  Write-Host ''
  Write-Host '==> Validate Phase 8 documentation'

  $readme = Get-Content `
    -LiteralPath 'README.md' `
    -Raw `
    -Encoding UTF8

  $requiredReadmeText = @(
    'Notifications and dead-letter operations',
    'GET  /api/v1/notifications',
    'GET  /api/v1/admin/outbox/dead-letters',
    'POST /api/v1/admin/outbox/dead-letters/{eventId}/replay',
    'Phase 8 verification',
    'verify-phase-8.ps1',
    'verify-phase-8.sh'
  )

  foreach ($expectedText in $requiredReadmeText) {
    if ($readme -notmatch [regex]::Escape($expectedText)) {
      throw "README is missing required text: $expectedText"
    }
  }

  $adr = Get-Content `
    -LiteralPath 'docs/adr/0012-notifications-and-dead-letter-operations.md' `
    -Raw `
    -Encoding UTF8

  $requiredAdrText = @(
    'payment.completed.v1',
    'one PostgreSQL transaction',
    'unique source-event identifier',
    'PENDING',
    'DELIVERING',
    'DELIVERED',
    'DEAD_LETTER',
    'Only an authenticated `ADMIN`',
    'immutable replay-audit record'
  )

  foreach ($expectedText in $requiredAdrText) {
    if ($adr -notmatch [regex]::Escape($expectedText)) {
      throw "ADR 0012 is missing required text: $expectedText"
    }
  }

  $architecture = Get-Content `
    -LiteralPath 'docs/architecture/overview.md' `
    -Raw `
    -Encoding UTF8

  $requiredArchitectureText = @(
    'Phase 8 implements',
    'Migration version 14',
    'Migration version 15',
    'stable bounded published-event pages',
    'owner-token leases',
    'immutable replay-audit evidence',
    'public outbox dead-letter operations boundary'
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
    'Phase 8 acceptance evidence',
    'Published outbox events are exposed through a narrow public read API | Completed',
    'Notification creation is idempotent by source event identifier | Completed',
    'Replay preserves the immutable event contract and records audit evidence | Completed',
    'PowerShell and Bash Phase 8 verifiers exist',
    'Composite Phase 8 verifier passes'
  )

  foreach ($expectedText in $requiredLedgerText) {
    if ($ledger -notmatch [regex]::Escape($expectedText)) {
      throw "Progress ledger is missing required text: $expectedText"
    }
  }

  Write-Host ''
  Write-Host '==> Validate Phase 8 implementation contracts'

  $notificationMigration = Get-Content `
    -LiteralPath 'backend/src/main/resources/db/migration/V14__create_notification_consumer.sql' `
    -Raw `
    -Encoding UTF8

  $requiredNotificationMigrationText = @(
    'CREATE TABLE notification_consumer_checkpoint',
    'CREATE TABLE notification',
    'CREATE TABLE notification_consumer_failure',
    'source_event_id',
    'UNIQUE',
    'PENDING',
    'DELIVERING',
    'DELIVERED',
    'DEAD_LETTER'
  )

  foreach ($expectedText in $requiredNotificationMigrationText) {
    if (
      $notificationMigration -notmatch
        [regex]::Escape($expectedText)
    ) {
      throw (
        'Migration V14 is missing required text: ' +
        $expectedText
      )
    }
  }

  $replayMigration = Get-Content `
    -LiteralPath 'backend/src/main/resources/db/migration/V15__add_outbox_dead_letter_replay.sql' `
    -Raw `
    -Encoding UTF8

  $requiredReplayMigrationText = @(
    'replay_count',
    'last_replayed_at',
    'CREATE TABLE outbox_replay_audit',
    'actor_identity_user_id',
    'reason',
    'reject_outbox_replay_audit_mutation',
    'BEFORE UPDATE OR DELETE'
  )

  foreach ($expectedText in $requiredReplayMigrationText) {
    if (
      $replayMigration -notmatch
        [regex]::Escape($expectedText)
    ) {
      throw (
        'Migration V15 is missing required text: ' +
        $expectedText
      )
    }
  }

  $notificationRepository = Get-Content `
    -LiteralPath 'backend/src/main/java/com/samharrison/payments/notification/internal/NotificationRepository.java' `
    -Raw `
    -Encoding UTF8

  if (
    $notificationRepository -notmatch
      [regex]::Escape('FOR UPDATE SKIP LOCKED')
  ) {
    throw 'Notification repository is missing SKIP LOCKED claiming.'
  }

  $consumer = Get-Content `
    -LiteralPath 'backend/src/main/java/com/samharrison/payments/notification/internal/NotificationEventConsumer.java' `
    -Raw `
    -Encoding UTF8

  $requiredConsumerText = @(
    'payment.completed.v1',
    'NotificationConsumerFailure',
    'checkpoint',
    'existsBySourceEventId'
  )

  foreach ($expectedText in $requiredConsumerText) {
    if ($consumer -notmatch [regex]::Escape($expectedText)) {
      throw "Notification consumer is missing required text: $expectedText"
    }
  }

  $replayService = Get-Content `
    -LiteralPath 'backend/src/main/java/com/samharrison/payments/outbox/internal/OutboxDeadLetterOperationsService.java' `
    -Raw `
    -Encoding UTF8

  $requiredReplayServiceText = @(
    'DEAD_LETTER',
    'expectedVersion',
    'OutboxReplayAudit.recorded',
    'event.replay'
  )

  foreach ($expectedText in $requiredReplayServiceText) {
    if (
      $replayService -notmatch
        [regex]::Escape($expectedText)
    ) {
      throw "Replay service is missing required text: $expectedText"
    }
  }

  $deadLetterPanel = Get-Content `
    -LiteralPath 'frontend/src/features/operations/components/DeadLetterPanel.tsx' `
    -Raw `
    -Encoding UTF8

  $requiredDeadLetterPanelText = @(
    'Outbox dead-letter recovery',
    'Inspect immutable payload',
    'Replay reason',
    'expectedVersion',
    'Replay queued'
  )

  foreach ($expectedText in $requiredDeadLetterPanelText) {
    if (
      $deadLetterPanel -notmatch
        [regex]::Escape($expectedText)
    ) {
      throw "Dead-letter panel is missing required text: $expectedText"
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

  $expectedMigrationVersions = @(1..15)
  $phase8MigrationVersions = @(
    $migrationVersions |
      Select-Object -First 15
  )

  if (
    ($phase8MigrationVersions -join ',') -ne
      ($expectedMigrationVersions -join ',')
  ) {
    throw (
      'Expected Flyway migrations 1 through 15 to remain present, found: ' +
      ($migrationVersions -join ', ')
    )
  }

  Write-Host ''
  Write-Host '==> Run Phase 7 baseline verification'

  & (Join-Path $PSScriptRoot 'verify-phase-7.ps1')

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
  Write-Host 'Phase 8 verification passed.'
}
finally {
  Pop-Location
}
