param(
    [switch]$StaticOnly
)

$ErrorActionPreference = 'Stop'

$repositoryRoot = Split-Path -Parent $PSScriptRoot

$requiredFiles = @(
    'docs/adr/0016-security-hardening-and-threat-model.md',
    'docs/security/threat-model.md',
    'backend/src/main/java/com/samharrison/payments/shared/config/SecurityRateLimitProperties.java',
    'backend/src/main/java/com/samharrison/payments/shared/infrastructure/web/FixedWindowRateLimiter.java',
    'backend/src/main/java/com/samharrison/payments/shared/infrastructure/web/SecurityHeadersFilter.java',
    'backend/src/main/java/com/samharrison/payments/shared/infrastructure/web/SecurityRateLimitingFilter.java',
    'backend/src/test/java/com/samharrison/payments/shared/infrastructure/web/FixedWindowRateLimiterTest.java',
    'backend/src/test/java/com/samharrison/payments/shared/infrastructure/web/SecurityHeadersFilterTest.java',
    'backend/src/test/java/com/samharrison/payments/shared/infrastructure/web/SecurityRateLimitingFilterTest.java',
    'backend/src/test/java/com/samharrison/payments/shared/infrastructure/web/RequestCompletionLoggingFilterTest.java'
)

foreach ($requiredFile in $requiredFiles) {
    $path = Join-Path $repositoryRoot $requiredFile
    if (-not (Test-Path -LiteralPath $path -PathType Leaf)) {
        throw "Required Phase 12 file is missing: $requiredFile"
    }
}

$application = Get-Content -LiteralPath (Join-Path $repositoryRoot 'backend/src/main/resources/application.yml') -Raw
foreach ($requiredText in @(
    'max-file-size: 1MB',
    'max-request-size: 2MB',
    'SECURITY_RATE_LIMIT_ENABLED:true'
)) {
if (-not $application.Contains($requiredText)) {
        throw "application.yml is missing: $requiredText"
    }
}

$environmentExample = Get-Content -LiteralPath (Join-Path $repositoryRoot '.env.example') -Raw
if (-not $environmentExample.Contains('SECURITY_RATE_LIMIT_ENABLED=true')) {
    throw '.env.example is missing the Phase 12 rate-limit setting.'
}

$headerFilter = Get-Content -LiteralPath (Join-Path $repositoryRoot 'backend/src/main/java/com/samharrison/payments/shared/infrastructure/web/SecurityHeadersFilter.java') -Raw
if (-not $headerFilter.Contains('X-Content-Type-Options')) {
    throw 'Security headers filter is missing nosniff protection.'
}

$rateFilter = Get-Content -LiteralPath (Join-Path $repositoryRoot 'backend/src/main/java/com/samharrison/payments/shared/infrastructure/web/SecurityRateLimitingFilter.java') -Raw
if (-not $rateFilter.Contains('Retry-After')) {
    throw 'Rate-limiting filter is missing retry guidance.'
}

$sourceFiles = Get-ChildItem -Path (Join-Path $repositoryRoot 'backend/src'), (Join-Path $repositoryRoot 'frontend/src') -File -Recurse
$secretPatterns = @(
    'BEGIN (RSA|OPENSSH|EC|DSA) PRIVATE KEY',
    'AKIA[0-9A-Z]{16}',
    'Authorization:\s*Bearer\s+[A-Za-z0-9._-]{20,}'
)
foreach ($sourceFile in $sourceFiles) {
    $content = Get-Content -LiteralPath $sourceFile.FullName -Raw
    foreach ($secretPattern in $secretPatterns) {
        if ($content -match $secretPattern) {
            throw "Potential hard-coded credential found in $($sourceFile.FullName)."
        }
    }
}

Push-Location $repositoryRoot
try {
    git diff --check

    if (-not $StaticOnly) {
        $taskGradleHome = if ($env:GRADLE_USER_HOME) { $env:GRADLE_USER_HOME } else { Join-Path $repositoryRoot '.gradle-phase-12' }
        $env:GRADLE_USER_HOME = $taskGradleHome
        & (Join-Path $repositoryRoot 'backend/gradlew') clean test bootJar --no-daemon
        corepack pnpm --dir (Join-Path $repositoryRoot 'frontend') install --frozen-lockfile
        corepack pnpm --dir (Join-Path $repositoryRoot 'frontend') lint
        corepack pnpm --dir (Join-Path $repositoryRoot 'frontend') test
        corepack pnpm --dir (Join-Path $repositoryRoot 'frontend') build
    }
} finally {
    Pop-Location
}

Write-Host 'Phase 12 static verification passed.'
