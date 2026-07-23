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
  "docs/adr/0012-notifications-and-dead-letter-operations.md"
  "docs/progress/ledger.md"

  "backend/src/main/resources/db/migration/V14__create_notification_consumer.sql"
  "backend/src/main/resources/db/migration/V15__add_outbox_dead_letter_replay.sql"

  "backend/src/main/java/com/samharrison/payments/outbox/PublishedOutboxEventReader.java"
  "backend/src/main/java/com/samharrison/payments/outbox/internal/PublishedOutboxEventReaderService.java"
  "backend/src/main/java/com/samharrison/payments/outbox/internal/OutboxDeadLetterOperationsService.java"
  "backend/src/main/java/com/samharrison/payments/outbox/internal/OutboxReplayAudit.java"

  "backend/src/main/java/com/samharrison/payments/notification/internal/NotificationEventConsumer.java"
  "backend/src/main/java/com/samharrison/payments/notification/internal/NotificationClaimingService.java"
  "backend/src/main/java/com/samharrison/payments/notification/internal/NotificationDeliveryProcessor.java"
  "backend/src/main/java/com/samharrison/payments/notification/internal/NotificationQueryController.java"
  "backend/src/main/java/com/samharrison/payments/notification/internal/OutboxDeadLetterController.java"

  "backend/src/test/java/com/samharrison/payments/notification/internal/NotificationEventConsumerIntegrationTest.java"
  "backend/src/test/java/com/samharrison/payments/notification/internal/NotificationDeliveryIntegrationTest.java"
  "backend/src/test/java/com/samharrison/payments/notification/internal/NotificationOperationsIntegrationTest.java"
  "backend/src/test/java/com/samharrison/payments/notification/internal/NotificationHttpIntegrationTest.java"
  "backend/src/test/java/com/samharrison/payments/ModularityTest.java"

  "frontend/src/features/notifications/components/NotificationPanel.tsx"
  "frontend/src/features/notifications/components/NotificationPanel.test.tsx"
  "frontend/src/features/operations/components/DeadLetterPanel.tsx"
  "frontend/src/features/operations/components/DeadLetterPanel.test.tsx"
  "frontend/src/features/operations/api/replayDeadLetter.ts"
  "frontend/src/features/identity/hooks/sessionCache.ts"

  "scripts/verify-phase-7.ps1"
  "scripts/verify-phase-7.sh"
  "scripts/verify-phase-8.ps1"
  "scripts/verify-phase-8.sh"
)

fail() {
  printf 'Phase 8 verification failed: %s\n' "$1" >&2
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

printf '==> Check required Phase 8 files\n'

for file in "${REQUIRED_FILES[@]}"; do
  if [[ ! -f "${file}" ]]; then
    fail "required file is missing: ${file}"
  fi

  if [[ ! -s "${file}" ]]; then
    fail "required file is empty: ${file}"
  fi
done

printf '\n==> Validate Phase 8 documentation\n'

readonly REQUIRED_README_TEXT=(
  "Notifications and dead-letter operations"
  "GET  /api/v1/notifications"
  "GET  /api/v1/admin/outbox/dead-letters"
  "POST /api/v1/admin/outbox/dead-letters/{eventId}/replay"
  "Phase 8 verification"
  "verify-phase-8.ps1"
  "verify-phase-8.sh"
)

for expected_text in "${REQUIRED_README_TEXT[@]}"; do
  require_text \
    "README.md" \
    "${expected_text}" \
    "README"
done

readonly REQUIRED_ADR_TEXT=(
  "payment.completed.v1"
  "one PostgreSQL transaction"
  "unique source-event identifier"
  "PENDING"
  "DELIVERING"
  "DELIVERED"
  "DEAD_LETTER"
  'Only an authenticated `ADMIN`'
  "immutable replay-audit record"
)

for expected_text in "${REQUIRED_ADR_TEXT[@]}"; do
  require_text \
    "docs/adr/0012-notifications-and-dead-letter-operations.md" \
    "${expected_text}" \
    "ADR 0012"
done

readonly REQUIRED_ARCHITECTURE_TEXT=(
  "Phase 8 implements"
  "Migration version 14"
  "Migration version 15"
  "stable bounded published-event pages"
  "owner-token leases"
  "immutable replay-audit evidence"
  "public outbox dead-letter operations boundary"
)

for expected_text in "${REQUIRED_ARCHITECTURE_TEXT[@]}"; do
  require_text \
    "docs/architecture/overview.md" \
    "${expected_text}" \
    "architecture overview"
done

readonly REQUIRED_LEDGER_TEXT=(
  "Phase 8 acceptance evidence"
  "Published outbox events are exposed through a narrow public read API | Completed"
  "Notification creation is idempotent by source event identifier | Completed"
  "Replay preserves the immutable event contract and records audit evidence | Completed"
  "PowerShell and Bash Phase 8 verifiers exist"
  "Composite Phase 8 verifier passes"
)

for expected_text in "${REQUIRED_LEDGER_TEXT[@]}"; do
  require_text \
    "docs/progress/ledger.md" \
    "${expected_text}" \
    "progress ledger"
done

printf '\n==> Validate Phase 8 implementation contracts\n'

readonly REQUIRED_NOTIFICATION_MIGRATION_TEXT=(
  "CREATE TABLE notification_consumer_checkpoint"
  "CREATE TABLE notification"
  "CREATE TABLE notification_consumer_failure"
  "source_event_id"
  "UNIQUE"
  "PENDING"
  "DELIVERING"
  "DELIVERED"
  "DEAD_LETTER"
)

for expected_text in "${REQUIRED_NOTIFICATION_MIGRATION_TEXT[@]}"; do
  require_text \
    "backend/src/main/resources/db/migration/V14__create_notification_consumer.sql" \
    "${expected_text}" \
    "migration V14"
done

readonly REQUIRED_REPLAY_MIGRATION_TEXT=(
  "replay_count"
  "last_replayed_at"
  "CREATE TABLE outbox_replay_audit"
  "actor_identity_user_id"
  "reason"
  "reject_outbox_replay_audit_mutation"
  "BEFORE UPDATE OR DELETE"
)

for expected_text in "${REQUIRED_REPLAY_MIGRATION_TEXT[@]}"; do
  require_text \
    "backend/src/main/resources/db/migration/V15__add_outbox_dead_letter_replay.sql" \
    "${expected_text}" \
    "migration V15"
done

require_text \
  "backend/src/main/java/com/samharrison/payments/notification/internal/NotificationRepository.java" \
  "FOR UPDATE SKIP LOCKED" \
  "notification repository"

readonly REQUIRED_CONSUMER_TEXT=(
  "payment.completed.v1"
  "NotificationConsumerFailure"
  "checkpoint"
  "existsBySourceEventId"
)

for expected_text in "${REQUIRED_CONSUMER_TEXT[@]}"; do
  require_text \
    "backend/src/main/java/com/samharrison/payments/notification/internal/NotificationEventConsumer.java" \
    "${expected_text}" \
    "notification consumer"
done

readonly REQUIRED_REPLAY_SERVICE_TEXT=(
  "DEAD_LETTER"
  "expectedVersion"
  "OutboxReplayAudit.recorded"
  "event.replay"
)

for expected_text in "${REQUIRED_REPLAY_SERVICE_TEXT[@]}"; do
  require_text \
    "backend/src/main/java/com/samharrison/payments/outbox/internal/OutboxDeadLetterOperationsService.java" \
    "${expected_text}" \
    "replay service"
done

readonly REQUIRED_DEAD_LETTER_PANEL_TEXT=(
  "Outbox dead-letter recovery"
  "Inspect immutable payload"
  "Replay reason"
  "expectedVersion"
  "Replay queued"
)

for expected_text in "${REQUIRED_DEAD_LETTER_PANEL_TEXT[@]}"; do
  require_text \
    "frontend/src/features/operations/components/DeadLetterPanel.tsx" \
    "${expected_text}" \
    "dead-letter panel"
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
  seq 1 15 |
    paste -sd, -
)"

readonly actual_versions="$(
  printf '%s\n' "${migration_versions[@]:0:15}" |
    paste -sd, -
)"

if [[ "${actual_versions}" != "${expected_versions}" ]]; then
  fail "expected Flyway migrations 1 through 15, found: ${migration_versions[*]}"
fi

printf '\n==> Run Phase 7 baseline verification\n'
bash scripts/verify-phase-7.sh

printf '\n==> Check unstaged whitespace\n'
git diff --check

printf '\n==> Check staged whitespace\n'
git diff --cached --check

printf '\nPhase 8 verification passed.\n'
