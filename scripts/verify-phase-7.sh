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
  "docs/adr/0005-transactional-outbox.md"
  "docs/adr/0011-asynchronous-events-and-outbox.md"
  "docs/progress/ledger.md"

  "backend/src/main/resources/db/migration/V13__create_transactional_outbox.sql"

  "backend/src/main/java/com/samharrison/payments/outbox/OutboxEventAppender.java"
  "backend/src/main/java/com/samharrison/payments/outbox/OutboxEventRequest.java"
  "backend/src/main/java/com/samharrison/payments/outbox/package-info.java"
  "backend/src/main/java/com/samharrison/payments/outbox/internal/OutboxEvent.java"
  "backend/src/main/java/com/samharrison/payments/outbox/internal/OutboxEventRepository.java"
  "backend/src/main/java/com/samharrison/payments/outbox/internal/OutboxClaimingService.java"
  "backend/src/main/java/com/samharrison/payments/outbox/internal/OutboxPublicationFinalizer.java"
  "backend/src/main/java/com/samharrison/payments/outbox/internal/OutboxPublisher.java"
  "backend/src/main/java/com/samharrison/payments/outbox/internal/LoggingOutboxTransport.java"

  "backend/src/main/java/com/samharrison/payments/payment/internal/PaymentCompletedOutboxEventFactory.java"
  "backend/src/main/java/com/samharrison/payments/payment/internal/PaymentPostingTransaction.java"

  "backend/src/test/java/com/samharrison/payments/outbox/OutboxEventRequestTest.java"
  "backend/src/test/java/com/samharrison/payments/outbox/internal/OutboxEventTest.java"
  "backend/src/test/java/com/samharrison/payments/outbox/internal/OutboxPublicationIntegrationTest.java"
  "backend/src/test/java/com/samharrison/payments/payment/internal/PaymentCompletedOutboxEventFactoryTest.java"
  "backend/src/test/java/com/samharrison/payments/payment/internal/PaymentProcessingIntegrationTest.java"
  "backend/src/test/java/com/samharrison/payments/ModularityTest.java"

  "scripts/verify-phase-6.ps1"
  "scripts/verify-phase-6.sh"
  "scripts/verify-phase-7.ps1"
  "scripts/verify-phase-7.sh"
)

fail() {
  printf 'Phase 7 verification failed: %s\n' "$1" >&2
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

printf '==> Check required Phase 7 files\n'

for file in "${REQUIRED_FILES[@]}"; do
  if [[ ! -f "${file}" ]]; then
    fail "required file is missing: ${file}"
  fi

  if [[ ! -s "${file}" ]]; then
    fail "required file is empty: ${file}"
  fi
done

printf '\n==> Validate Phase 7 documentation\n'

readonly REQUIRED_README_TEXT=(
  "Transactional outbox"
  "payment.completed.v1"
  "FOR UPDATE SKIP LOCKED"
  "Phase 7 verification"
  "verify-phase-7.ps1"
  "verify-phase-7.sh"
)

for expected_text in "${REQUIRED_README_TEXT[@]}"; do
  require_text \
    "README.md" \
    "${expected_text}" \
    "README"
done

readonly REQUIRED_ADR_TEXT=(
  "payment.completed.v1"
  "same PostgreSQL transaction"
  "SKIP LOCKED"
  "PUBLISHING"
  "PUBLISHED"
  "DEAD_LETTER"
)

for expected_text in "${REQUIRED_ADR_TEXT[@]}"; do
  require_text \
    "docs/adr/0011-asynchronous-events-and-outbox.md" \
    "${expected_text}" \
    "ADR 0011"
done

readonly REQUIRED_ARCHITECTURE_TEXT=(
  "Asynchronous events and outbox"
  "payment.completed.v1"
  "Migration version 13"
  "owner-token publication lease"
  "outbox event commit atomically"
)

for expected_text in "${REQUIRED_ARCHITECTURE_TEXT[@]}"; do
  require_text \
    "docs/architecture/overview.md" \
    "${expected_text}" \
    "architecture overview"
done

readonly REQUIRED_LEDGER_TEXT=(
  "Phase 7 acceptance evidence"
  "Completed payments create one outbox event atomically"
  "Bounded event claiming uses owner tokens and leases"
  "PowerShell and Bash Phase 7 verifiers exist"
  "Composite Phase 7 verifier passes"
)

for expected_text in "${REQUIRED_LEDGER_TEXT[@]}"; do
  require_text \
    "docs/progress/ledger.md" \
    "${expected_text}" \
    "progress ledger"
done

printf '\n==> Validate Phase 7 implementation contracts\n'

readonly REQUIRED_MIGRATION_TEXT=(
  "CREATE TABLE outbox_event"
  "PENDING"
  "PUBLISHING"
  "PUBLISHED"
  "DEAD_LETTER"
  "payload IS JSON OBJECT"
  "publication_owner_token"
  "publication_lease_expires_at"
)

for expected_text in "${REQUIRED_MIGRATION_TEXT[@]}"; do
  require_text \
    "backend/src/main/resources/db/migration/V13__create_transactional_outbox.sql" \
    "${expected_text}" \
    "migration V13"
done

require_text \
  "backend/src/main/java/com/samharrison/payments/outbox/internal/OutboxEventRepository.java" \
  "FOR UPDATE SKIP LOCKED" \
  "outbox repository"

readonly REQUIRED_FACTORY_TEXT=(
  "payment.completed.v1"
  "amountMinorUnits"
  "ledgerTransactionId"
  "completedAt"
)

for expected_text in "${REQUIRED_FACTORY_TEXT[@]}"; do
  require_text \
    "backend/src/main/java/com/samharrison/payments/payment/internal/PaymentCompletedOutboxEventFactory.java" \
    "${expected_text}" \
    "payment event factory"
done

printf '\n==> Validate Flyway migration sequence\n'

mapfile -t migration_versions < <(
  find backend/src/main/resources/db/migration \
    -maxdepth 1 \
    -type f \
    -name 'V*__*.sql' \
    -printf '%f\n' |
  sed -E 's/^V([0-9]+)__.*/\1/' |
  sort -n
)

readonly expected_versions="$(
  seq 1 13 |
    paste -sd, -
)"

readonly actual_versions="$(
  printf '%s\n' "${migration_versions[@]:0:13}" |
    paste -sd, -
)"

if [[ "${actual_versions}" != "${expected_versions}" ]]; then
  fail "expected Flyway migrations 1 through 13, found: ${migration_versions[*]}"
fi

printf '\n==> Run Phase 6 baseline verification\n'
bash scripts/verify-phase-6.sh

printf '\n==> Check unstaged whitespace\n'
git diff --check

printf '\n==> Check staged whitespace\n'
git diff --cached --check

printf '\nPhase 7 verification passed.\n'
