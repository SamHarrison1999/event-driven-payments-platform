$ErrorActionPreference = 'Stop'
Set-StrictMode -Version Latest

$repositoryRoot = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path
$scriptPath = Join-Path $repositoryRoot 'load-tests/payment-submission.js'
$readmePath = Join-Path $repositoryRoot 'load-tests/README.md'

if (-not (Test-Path -LiteralPath $scriptPath -PathType Leaf)) {
    throw 'The Phase 11 payment load-test script is missing.'
}

if (-not (Test-Path -LiteralPath $readmePath -PathType Leaf)) {
    throw 'The Phase 11 payment load-test README is missing.'
}

$script = Get-Content -LiteralPath $scriptPath -Raw
$readme = Get-Content -LiteralPath $readmePath -Raw

$scriptPatterns = @(
    'constant-arrival-rate',
    'api/v1/payments',
    'PAYMENTS_SESSION',
    'CSRF_TOKEN',
    'SOURCE_ACCOUNT_ID',
    'DESTINATION_ACCOUNT_ID',
    'Idempotency-Key',
    'payment_submission',
    'http_req_failed',
    'payment_submission_duration'
)

foreach ($pattern in $scriptPatterns) {
    if ($script -notmatch [regex]::Escape($pattern)) {
        throw "Load-test script is missing required contract: $pattern"
    }
}

$readmePatterns = @(
    'authenticated',
    'CSRF',
    'disposable local',
    'provisional harness checks',
    'actual',
    'grafana/k6:1.6.1'
)

foreach ($pattern in $readmePatterns) {
    if ($readme -notmatch [regex]::Escape($pattern)) {
        throw "Load-test documentation is missing required guidance: $pattern"
    }
}

if ($script -match '(?i)(password|secret|token)\s*[:=]\s*["''][^"'']+["'']') {
    throw 'The load-test script appears to contain a hard-coded secret.'
}

Push-Location $repositoryRoot
try {
    & git diff --check
    if ($LASTEXITCODE -ne 0) {
        throw 'git diff --check failed.'
    }
}
finally {
    Pop-Location
}

Write-Output 'Phase 11 load-test static verification passed.'
