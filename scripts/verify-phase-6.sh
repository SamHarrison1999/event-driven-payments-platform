#!/usr/bin/env bash

set -euo pipefail

readonly SCRIPT_DIRECTORY="$(
  cd "$(dirname "${BASH_SOURCE[0]}")" &&
    pwd
)"

readonly REPOSITORY_ROOT="$(
  cd "${SCRIPT_DIRECTORY}/.." &&
    pwd
)"

readonly REQUIRED_FILES=(
  "README.md"
  "docs/architecture/overview.md"
  "docs/adr/0010-frontend-payment-experience.md"
  "docs/progress/ledger.md"

  "frontend/package.json"
  "frontend/pnpm-lock.yaml"
  "frontend/src/App.tsx"
  "frontend/src/App.test.tsx"

  "frontend/src/shared/api/apiClient.ts"
  "frontend/src/shared/api/apiProblem.ts"
  "frontend/src/shared/api/csrfToken.ts"
  "frontend/src/shared/money/gbp.ts"
  "frontend/src/shared/storage/customerSessionStorage.ts"

  "frontend/src/features/identity/components/SessionBoundary.tsx"
  "frontend/src/features/identity/components/LoginForm.tsx"
  "frontend/src/features/identity/hooks/expireCurrentSession.ts"
  "frontend/src/features/accounts/components/CustomerAccountsPanel.tsx"

  "frontend/src/features/payments/api/getPayment.ts"
  "frontend/src/features/payments/api/submitPaymentIdempotently.ts"
  "frontend/src/features/payments/components/PaymentAmountInput.tsx"
  "frontend/src/features/payments/components/PaymentCreationForm.tsx"
  "frontend/src/features/payments/components/PaymentLookup.tsx"
  "frontend/src/features/payments/components/PaymentReceipt.tsx"
  "frontend/src/features/payments/idempotency/paymentSubmissionEnvelope.ts"
  "frontend/src/features/payments/model/payment.ts"
  "frontend/src/features/payments/model/paymentDraft.ts"

  "scripts/verify-phase-5.ps1"
  "scripts/verify-phase-5.sh"
  "scripts/verify-phase-6.ps1"
  "scripts/verify-phase-6.sh"
)

fail() {
  printf 'Phase 6 verification failed: %s\n' "$1" >&2
  exit 1
}

require_text() {
  local file="$1"
  local expected_text="$2"
  local description="$3"

  if ! grep -Fq "${expected_text}" "${file}"; then
    fail "${description} is missing required text: ${expected_text}"
  fi
}

cd "${REPOSITORY_ROOT}"

printf '==> Check required Phase 6 files\n'

for file in "${REQUIRED_FILES[@]}"; do
  if [[ ! -f "${file}" ]]; then
    fail "required file is missing: ${file}"
  fi

  if [[ ! -s "${file}" ]]; then
    fail "required file is empty: ${file}"
  fi
done

printf '\n==> Validate Phase 6 documentation\n'

readonly REQUIRED_README_TEXT=(
  "Phase 6 verification"
  "verify-phase-6.ps1"
  "verify-phase-6.sh"
  "authenticated payment workspace"
  "customer-owned payment lookup"
)

for expected_text in "${REQUIRED_README_TEXT[@]}"; do
  require_text \
    "README.md" \
    "${expected_text}" \
    "README"
done

readonly REQUIRED_ADR_TEXT=(
  "Frontend payment experience"
  "Payment lookup uses:"
  "Every control has a visible label and accessible name."
  "Results use an appropriate live region"
  "error summary or result heading"
  "The cumulative Phase 6 verifier runs prior-phase verification"
)

for expected_text in "${REQUIRED_ADR_TEXT[@]}"; do
  require_text \
    "docs/adr/0010-frontend-payment-experience.md" \
    "${expected_text}" \
    "ADR 0010"
done

readonly REQUIRED_ARCHITECTURE_TEXT=(
  "Frontend payment experience"
  "owned-account and exact GBP balance presentation"
  "customer-owned payment lookup"
)

for expected_text in "${REQUIRED_ARCHITECTURE_TEXT[@]}"; do
  require_text \
    "docs/architecture/overview.md" \
    "${expected_text}" \
    "architecture overview"
done

readonly REQUIRED_LEDGER_TEXT=(
  "Phase 6 acceptance evidence"
  "Customer-owned payment lookup works"
  "PowerShell and Bash Phase 6 verifiers exist"
  "Composite Phase 6 verifier passes"
)

for expected_text in "${REQUIRED_LEDGER_TEXT[@]}"; do
  require_text \
    "docs/progress/ledger.md" \
    "${expected_text}" \
    "progress ledger"
done

printf '\n==> Run Phase 5 baseline verification\n'
bash scripts/verify-phase-5.sh

printf '\n==> Check unstaged whitespace\n'
git diff --check

printf '\n==> Check staged whitespace\n'
git diff --cached --check

printf '\nPhase 6 verification passed.\n'
