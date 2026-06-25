[CmdletBinding()]
param()

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

$repositoryRoot = Split-Path -Parent $PSScriptRoot

$requiredFiles = @(
  'README.md',
  'docs/architecture/overview.md',
  'docs/progress/ledger.md',
  'docs/adr/0007-password-policy-and-hashing.md',

  'backend/src/main/resources/db/migration/V2__create_identity_schema.sql',
  'backend/src/main/resources/db/migration/V3__create_jdbc_session_schema.sql',
  'backend/src/main/resources/db/migration/V4__create_identity_security_event_log.sql',

  'backend/src/main/java/com/samharrison/payments/identity/internal/IdentityUser.java',
  'backend/src/main/java/com/samharrison/payments/identity/internal/PasswordPolicy.java',
  'backend/src/main/java/com/samharrison/payments/identity/internal/CustomerRegistrationController.java',
  'backend/src/main/java/com/samharrison/payments/identity/internal/IdentitySecurityConfiguration.java',
  'backend/src/main/java/com/samharrison/payments/identity/internal/IdentitySessionController.java',
  'backend/src/main/java/com/samharrison/payments/identity/internal/IdentityRoleManagementService.java',
  'backend/src/main/java/com/samharrison/payments/identity/internal/IdentitySecurityEvent.java',

  'backend/src/test/java/com/samharrison/payments/identity/internal/CustomerRegistrationIntegrationTest.java',
  'backend/src/test/java/com/samharrison/payments/identity/internal/IdentitySessionIntegrationTest.java',
  'backend/src/test/java/com/samharrison/payments/identity/internal/IdentityRoleManagementIntegrationTest.java',
  'backend/src/test/java/com/samharrison/payments/identity/internal/IdentitySecurityAuditIntegrationTest.java',

  'scripts/verify-phase-1.ps1',
  'scripts/verify-phase-1.sh',
  'scripts/verify-phase-2.ps1',
  'scripts/verify-phase-2.sh'
)

Push-Location $repositoryRoot

try {
  Write-Host '==> Check required Phase 2 files'

  foreach ($file in $requiredFiles) {
    if (-not (Test-Path -LiteralPath $file -PathType Leaf)) {
      throw "Required file is missing: $file"
    }

    if ((Get-Item -LiteralPath $file).Length -eq 0) {
      throw "Required file is empty: $file"
    }
  }

  Write-Host ''
  Write-Host '==> Validate Phase 2 documentation'

  $readme = Get-Content `
    -LiteralPath 'README.md' `
    -Raw `
    -Encoding UTF8

  $requiredReadmeText = @(
    'Phase 2',
    'Identity and access',
    'verify-phase-2.ps1',
    'POST   /api/v1/identity/registrations',
    'POST   /api/v1/identity/session',
    'this application does not process real money'
  )

  foreach ($expectedText in $requiredReadmeText) {
    if ($readme -notmatch [regex]::Escape($expectedText)) {
      throw "README is missing required text: $expectedText"
    }
  }

  Write-Host ''
  Write-Host '==> Check Flyway migration versions'

  $migrationVersions = @(
    Get-ChildItem `
      -LiteralPath 'backend/src/main/resources/db/migration' `
      -Filter 'V*__*.sql' |
      ForEach-Object {
        if ($_.Name -match '^V([^_]+)__') {
          $Matches[1]
        }
      }
  )

  $duplicateVersions = @(
    $migrationVersions |
      Group-Object |
      Where-Object {
        $_.Count -gt 1
      }
  )

  if ($duplicateVersions.Count -gt 0) {
    $versions = (
      $duplicateVersions |
        ForEach-Object {
          $_.Name
        }
    ) -join ', '

    throw "Duplicate Flyway migration versions: $versions"
  }

  Write-Host ''
  Write-Host '==> Run Phase 1 baseline verification'

  & (Join-Path $PSScriptRoot 'verify-phase-1.ps1')

  Write-Host ''
  Write-Host 'Phase 2 verification passed.'
}
finally {
  Pop-Location
}