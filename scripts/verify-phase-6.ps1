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
  'docs/adr/0010-frontend-payment-experience.md',
  'docs/progress/ledger.md',

  'frontend/package.json',
  'frontend/pnpm-lock.yaml',
  'frontend/src/App.tsx',
  'frontend/src/App.test.tsx',

  'frontend/src/shared/api/apiClient.ts',
  'frontend/src/shared/api/apiProblem.ts',
  'frontend/src/shared/api/csrfToken.ts',
  'frontend/src/shared/money/gbp.ts',
  'frontend/src/shared/storage/customerSessionStorage.ts',

  'frontend/src/features/identity/components/SessionBoundary.tsx',
  'frontend/src/features/identity/components/LoginForm.tsx',
  'frontend/src/features/identity/hooks/expireCurrentSession.ts',
  'frontend/src/features/accounts/components/CustomerAccountsPanel.tsx',

  'frontend/src/features/payments/api/getPayment.ts',
  'frontend/src/features/payments/api/submitPaymentIdempotently.ts',
  'frontend/src/features/payments/components/PaymentAmountInput.tsx',
  'frontend/src/features/payments/components/PaymentCreationForm.tsx',
  'frontend/src/features/payments/components/PaymentLookup.tsx',
  'frontend/src/features/payments/components/PaymentReceipt.tsx',
  'frontend/src/features/payments/idempotency/paymentSubmissionEnvelope.ts',
  'frontend/src/features/payments/model/payment.ts',
  'frontend/src/features/payments/model/paymentDraft.ts',

  'scripts/verify-phase-5.ps1',
  'scripts/verify-phase-5.sh',
  'scripts/verify-phase-6.ps1',
  'scripts/verify-phase-6.sh'
)

Push-Location $repositoryRoot

try {
  Write-Host '==> Check required Phase 6 files'

  foreach ($file in $requiredFiles) {
    if (-not (Test-Path -LiteralPath $file -PathType Leaf)) {
      throw "Required file is missing: $file"
    }

    if ((Get-Item -LiteralPath $file).Length -eq 0) {
      throw "Required file is empty: $file"
    }
  }

  Write-Host ''
  Write-Host '==> Validate Phase 6 documentation'

  $readme = Get-Content `
    -LiteralPath 'README.md' `
    -Raw `
    -Encoding UTF8

  $requiredReadmeText = @(
    'Phase 6 verification',
    'verify-phase-6.ps1',
    'verify-phase-6.sh',
    'authenticated payment workspace',
    'customer-owned payment lookup'
  )

  foreach ($expectedText in $requiredReadmeText) {
    if ($readme -notmatch [regex]::Escape($expectedText)) {
      throw "README is missing required text: $expectedText"
    }
  }

  $adr = Get-Content `
    -LiteralPath 'docs/adr/0010-frontend-payment-experience.md' `
    -Raw `
    -Encoding UTF8

  $requiredAdrText = @(
    'Frontend payment experience',
    'Payment lookup uses:',
    'Every control has a visible label and accessible name.',
    'Results use an appropriate live region',
    'error summary or result heading',
    'The cumulative Phase 6 verifier runs prior-phase verification'
  )

  foreach ($expectedText in $requiredAdrText) {
    if ($adr -notmatch [regex]::Escape($expectedText)) {
      throw "ADR 0010 is missing required text: $expectedText"
    }
  }

  $architecture = Get-Content `
    -LiteralPath 'docs/architecture/overview.md' `
    -Raw `
    -Encoding UTF8

  $requiredArchitectureText = @(
    'Frontend payment experience',
    'owned-account and exact GBP balance presentation',
    'customer-owned payment lookup'
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
    'Phase 6 acceptance evidence',
    'Customer-owned payment lookup works',
    'PowerShell and Bash Phase 6 verifiers exist',
    'Composite Phase 6 verifier passes'
  )

  foreach ($expectedText in $requiredLedgerText) {
    if ($ledger -notmatch [regex]::Escape($expectedText)) {
      throw "Progress ledger is missing required text: $expectedText"
    }
  }

  Write-Host ''
  Write-Host '==> Run Phase 5 baseline verification'

  & (Join-Path $PSScriptRoot 'verify-phase-5.ps1')

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
  Write-Host 'Phase 6 verification passed.'
}
finally {
  Pop-Location
}
