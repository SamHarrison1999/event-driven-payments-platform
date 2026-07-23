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
  "docs/adr/0006-idempotency-and-payment-lifecycle.md"
  "docs/adr/0009-synchronous-payment-orchestration.md"
  "docs/progress/ledger.md"

  "backend/src/main/resources/db/migration/V11__create_payment_and_idempotency_schema.sql"
  "backend/src/main/resources/db/migration/V12__allow_unknown_payment_account_references.sql"

  "backend/src/main/java/com/samharrison/payments/customer/CustomerOwnership.java"
  "backend/src/main/java/com/samharrison/payments/account/AccountPaymentMutation.java"
  "backend/src/main/java/com/samharrison/payments/account/AccountPaymentResult.java"
  "backend/src/main/java/com/samharrison/payments/account/internal/AccountPaymentMutationService.java"

  "backend/src/main/java/com/samharrison/payments/payment/internal/Payment.java"
  "backend/src/main/java/com/samharrison/payments/payment/internal/PaymentIdempotencyRecord.java"
  "backend/src/main/java/com/samharrison/payments/payment/internal/PaymentReservationCoordinator.java"
  "backend/src/main/java/com/samharrison/payments/payment/internal/PaymentPostingTransaction.java"
  "backend/src/main/java/com/samharrison/payments/payment/internal/PaymentProcessingCoordinator.java"
  "backend/src/main/java/com/samharrison/payments/payment/internal/PaymentSubmissionService.java"
  "backend/src/main/java/com/samharrison/payments/payment/internal/PaymentQueryService.java"
  "backend/src/main/java/com/samharrison/payments/payment/internal/PaymentController.java"

  "backend/src/test/java/com/samharrison/payments/account/internal/AccountPaymentMutationServiceIntegrationTest.java"
  "backend/src/test/java/com/samharrison/payments/payment/internal/PaymentTest.java"
  "backend/src/test/java/com/samharrison/payments/payment/internal/PaymentPersistenceIntegrationTest.java"
  "backend/src/test/java/com/samharrison/payments/payment/internal/PaymentReservationCoordinatorIntegrationTest.java"
  "backend/src/test/java/com/samharrison/payments/payment/internal/PaymentProcessingIntegrationTest.java"
  "backend/src/test/java/com/samharrison/payments/payment/internal/PaymentSubmissionHttpIntegrationTest.java"
  "backend/src/test/java/com/samharrison/payments/payment/internal/PaymentQueryHttpIntegrationTest.java"
  "backend/src/test/java/com/samharrison/payments/ModularityTest.java"

  "scripts/verify-phase-4.ps1"
  "scripts/verify-phase-4.sh"
  "scripts/verify-phase-5.ps1"
  "scripts/verify-phase-5.sh"
)

fail() {
  printf 'Phase 5 verification failed: %s\n' "$1" >&2
  exit 1
}

cd "${REPOSITORY_ROOT}"

printf '==> Check required Phase 5 files\n'

for file in "${REQUIRED_FILES[@]}"; do
  if [[ ! -f "${file}" ]]; then
    fail "required file is missing: ${file}"
  fi

  if [[ ! -s "${file}" ]]; then
    fail "required file is empty: ${file}"
  fi
done

printf '\n==> Validate Phase 5 documentation\n'

readonly REQUIRED_README_TEXT=(
  "Phase 5 — Synchronous payments"
  "POST /api/v1/payments"
  "GET  /api/v1/payments/{paymentId}"
  "Idempotency-Key"
  "verify-phase-5.ps1"
  "does not process real money"
)

for expected_text in "${REQUIRED_README_TEXT[@]}"; do
  if ! grep -Fq "${expected_text}" README.md; then
    fail "README is missing required text: ${expected_text}"
  fi
done

readonly REQUIRED_ARCHITECTURE_TEXT=(
  "Phase 5 — Synchronous payments"
  "durable idempotency reservation"
  "Migration version 11"
  "Migration version 12"
  "Payment reservation"
  "Core payment posting"
  "PAYMENT_INSUFFICIENT_FUNDS"
)

for expected_text in "${REQUIRED_ARCHITECTURE_TEXT[@]}"; do
  if ! grep -Fq \
    "${expected_text}" \
    docs/architecture/overview.md; then
    fail \
      "architecture overview is missing required text: ${expected_text}"
  fi
done

if ! grep -Fq \
  "Phase 5 does not create outbox or business-audit records" \
  docs/architecture/overview.md &&
  ! grep -Fq \
    "outbox event commit atomically" \
    docs/architecture/overview.md; then
  fail \
    "architecture overview is missing the Phase 5 or later outbox transaction-boundary evidence"
fi

readonly REQUIRED_LEDGER_TEXT=(
  "Phase 5 acceptance evidence"
  "Synchronous payments | Completed"
  "Domain, persistence and idempotency"
  "Account mutation and atomic processing"
  "HTTP, security and verification"
  "Complete backend regression passes"
)

for expected_text in "${REQUIRED_LEDGER_TEXT[@]}"; do
  if ! grep -Fq \
    "${expected_text}" \
    docs/progress/ledger.md; then
    fail \
      "progress ledger is missing required text: ${expected_text}"
  fi
done

printf '\n==> Validate Flyway migration sequence\n'

mapfile -t migration_versions < <(
  for migration in \
    backend/src/main/resources/db/migration/V*__*.sql; do
    basename "${migration}" |
      sed -E 's/^V([0-9]+)__.*/\1/'
  done |
    sort -n
)

readonly expected_migration_versions=(
  1 2 3 4 5 6 7 8 9 10 11 12
)

readonly phase5_migration_versions=(
  "${migration_versions[@]:0:12}"
)

if [[ "${phase5_migration_versions[*]}" != "${expected_migration_versions[*]}" ]]; then
  fail \
    "expected Flyway migrations 1 through 12 to remain present, found: ${migration_versions[*]}"
fi

printf '\n==> Run Phase 4 baseline verification\n'

bash scripts/verify-phase-4.sh

printf '\nPhase 5 verification passed.\n'
