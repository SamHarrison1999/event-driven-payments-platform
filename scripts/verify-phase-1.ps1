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
  '.github/workflows/ci.yml',
  '.env.example',
  'compose.yaml',
  'README.md',
  'backend/build.gradle.kts',
  'backend/settings.gradle.kts',
  'backend/gradlew',
  'backend/gradlew.bat',
  'backend/src/main/resources/application.yml',
  'backend/src/main/resources/db/migration/V1__establish_schema_baseline.sql',
  'backend/src/test/java/com/samharrison/payments/BackendIntegrationTest.java',
  'backend/src/test/java/com/samharrison/payments/ModularityTest.java',
  'frontend/package.json',
  'frontend/pnpm-lock.yaml',
  'frontend/src/App.tsx',
  'frontend/src/App.test.tsx',
  'frontend/src/features/system/api/getSystemInfo.test.ts',
  'frontend/src/features/system/api/getSystemInfo.ts',
  'frontend/vite.config.ts',
  'scripts/verify-phase-0.sh',
  'scripts/verify-phase-1.ps1'
)

Push-Location $repositoryRoot

try {
  Write-Host '==> Check required Phase 1 files'

  foreach ($file in $requiredFiles) {
    if (-not (Test-Path -LiteralPath $file -PathType Leaf)) {
      throw "Required file is missing: $file"
    }

    if ((Get-Item -LiteralPath $file).Length -eq 0) {
      throw "Required file is empty: $file"
    }
  }

  $readme = Get-Content `
        -LiteralPath 'README.md' `
        -Raw `
        -Encoding UTF8

  if (
  $readme -notmatch
    [regex]::Escape(
      'this application does not process real money'
    )
  ) {
    throw 'README does not contain the educational-use warning.'
  }

  Invoke-CheckedCommand `
        -Description 'Validate Docker Compose configuration' `
        -Command {
    docker compose config
  }

  Push-Location 'backend'

  try {
    Invoke-CheckedCommand `
            -Description 'Test and package backend' `
            -Command {
      .\gradlew.bat `
                    clean `
                    test `
                    bootJar `
                    --no-daemon
    }
  }
  finally {
    Pop-Location
  }

  Push-Location 'frontend'

  try {
    Invoke-CheckedCommand `
            -Description 'Install locked frontend dependencies' `
            -Command {
      pnpm install --frozen-lockfile
    }

    Invoke-CheckedCommand `
            -Description 'Lint frontend' `
            -Command {
      pnpm lint
    }

    Invoke-CheckedCommand `
            -Description 'Test frontend' `
            -Command {
      pnpm test
    }

    Invoke-CheckedCommand `
            -Description 'Build frontend' `
            -Command {
      pnpm build
    }
  }
  finally {
    Pop-Location
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
  Write-Host 'Phase 1 verification passed.'
}
finally {
  Pop-Location
}
