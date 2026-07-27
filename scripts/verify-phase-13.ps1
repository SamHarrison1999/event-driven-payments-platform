param(
    [switch]$StaticOnly
)

$ErrorActionPreference = 'Stop'

$repositoryRoot = Split-Path -Parent $PSScriptRoot
$composeFile = Join-Path $repositoryRoot 'compose.release.yaml'

function Assert-Contains {
    param(
        [Parameter(Mandatory = $true)]
        [string]$Path,

        [Parameter(Mandatory = $true)]
        [string]$Text
    )

    $content = Get-Content -LiteralPath (Join-Path $repositoryRoot $Path) -Raw
    if (-not $content.Contains($Text)) {
        throw "$Path is missing: $Text"
    }
}

$requiredFiles = @(
    'compose.release.yaml',
    'backend/Dockerfile',
    'backend/.dockerignore',
    'frontend/Dockerfile',
    'frontend/.dockerignore',
    'frontend/nginx.conf',
    'docs/adr/0017-reproducible-release-infrastructure.md'
)

foreach ($requiredFile in $requiredFiles) {
    $path = Join-Path $repositoryRoot $requiredFile
    if (-not (Test-Path -LiteralPath $path -PathType Leaf)) {
        throw "Required Phase 13 file is missing: $requiredFile"
    }
}

Assert-Contains -Path 'backend/build.gradle.kts' -Text 'payments-platform-backend.jar'
Assert-Contains -Path 'backend/Dockerfile' -Text 'USER payments:payments'
Assert-Contains -Path 'frontend/nginx.conf' -Text 'proxy_pass http://backend:8080'
Assert-Contains -Path 'compose.release.yaml' -Text 'condition: service_healthy'

Push-Location $repositoryRoot
try {
    & git diff --check
    if ($LASTEXITCODE -ne 0) {
        throw 'git diff --check failed.'
    }

    & docker compose -f $composeFile config --quiet
    if ($LASTEXITCODE -ne 0) {
        throw 'Release Compose configuration is invalid.'
    }

    if (-not $StaticOnly) {
        $port = if ($env:RELEASE_HTTP_PORT) {
            $env:RELEASE_HTTP_PORT
        } else {
            '8080'
        }

        & docker compose -f $composeFile build
        if ($LASTEXITCODE -ne 0) {
            throw 'Release image build failed.'
        }

        & docker compose -f $composeFile up -d
        if ($LASTEXITCODE -ne 0) {
            throw 'Release Compose startup failed.'
        }

        try {
            $deadline = (Get-Date).AddMinutes(2)
            do {
                try {
                    $health = Invoke-WebRequest `
                        -UseBasicParsing `
                        -Uri "http://localhost:$port/healthz" `
                        -TimeoutSec 5
                    if ($health.StatusCode -eq 200) {
                        break
                    }
                } catch {
                    Start-Sleep -Seconds 3
                }

                if ((Get-Date) -ge $deadline) {
                    throw 'Timed out waiting for the release frontend.'
                }
            } while ($true)

            $readiness = Invoke-WebRequest `
                -UseBasicParsing `
                -Uri "http://localhost:$port/actuator/health/readiness" `
                -TimeoutSec 10
            if ($readiness.StatusCode -ne 200) {
                throw 'Backend readiness smoke check failed.'
            }
        } finally {
            & docker compose -f $composeFile down --remove-orphans
            if ($LASTEXITCODE -ne 0) {
                throw 'Release Compose shutdown failed.'
            }
        }
    }
} finally {
    Pop-Location
}

if ($StaticOnly) {
    Write-Host 'Phase 13 static verification passed.'
} else {
    Write-Host 'Phase 13 release smoke verification passed.'
}
