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
  "docs/progress/ledger.md"
  "docs/adr/0007-password-policy-and-hashing.md"

  "backend/src/main/resources/db/migration/V2__create_identity_schema.sql"
  "backend/src/main/resources/db/migration/V3__create_jdbc_session_schema.sql"
  "backend/src/main/resources/db/migration/V4__create_identity_security_event_log.sql"

  "backend/src/main/java/com/samharrison/payments/identity/internal/IdentityUser.java"
  "backend/src/main/java/com/samharrison/payments/identity/internal/PasswordPolicy.java"
  "backend/src/main/java/com/samharrison/payments/identity/internal/CustomerRegistrationController.java"
  "backend/src/main/java/com/samharrison/payments/identity/internal/IdentitySecurityConfiguration.java"
  "backend/src/main/java/com/samharrison/payments/identity/internal/IdentitySessionController.java"
  "backend/src/main/java/com/samharrison/payments/identity/internal/IdentityRoleManagementService.java"
  "backend/src/main/java/com/samharrison/payments/identity/internal/IdentitySecurityEvent.java"

  "backend/src/test/java/com/samharrison/payments/identity/internal/CustomerRegistrationIntegrationTest.java"
  "backend/src/test/java/com/samharrison/payments/identity/internal/IdentitySessionIntegrationTest.java"
  "backend/src/test/java/com/samharrison/payments/identity/internal/IdentityRoleManagementIntegrationTest.java"
  "backend/src/test/java/com/samharrison/payments/identity/internal/IdentitySecurityAuditIntegrationTest.java"

  "scripts/verify-phase-1.ps1"
  "scripts/verify-phase-1.sh"
  "scripts/verify-phase-2.ps1"
  "scripts/verify-phase-2.sh"
)

fail() {
  printf 'Phase 2 verification failed: %s\n' "$1" >&2
  exit 1
}

cd "${REPOSITORY_ROOT}"

printf '==> Check required Phase 2 files\n'

for file in "${REQUIRED_FILES[@]}"; do
  if [[ ! -f "${file}" ]]; then
    fail "required file is missing: ${file}"
  fi

  if [[ ! -s "${file}" ]]; then
    fail "required file is empty: ${file}"
  fi
done

printf '\n==> Validate Phase 2 documentation\n'

readonly REQUIRED_README_TEXT=(
  "Phase 2"
  "Identity and access"
  "verify-phase-2.ps1"
  "POST   /api/v1/identity/registrations"
  "POST   /api/v1/identity/session"
  "this application does not process real money"
)

for expected_text in "${REQUIRED_README_TEXT[@]}"; do
  if ! grep -Fq "${expected_text}" README.md; then
    fail "README is missing required text: ${expected_text}"
  fi
done

printf '\n==> Check Flyway migration versions\n'

duplicate_versions="$(
  for migration in \
    backend/src/main/resources/db/migration/V*__*.sql; do
    basename "${migration}" |
      sed -E 's/^V([^_]+)__.*/\1/'
  done |
    sort |
    uniq -d
)"

if [[ -n "${duplicate_versions}" ]]; then
  fail "duplicate Flyway migration versions: ${duplicate_versions}"
fi

printf '\n==> Run Phase 1 baseline verification\n'

bash scripts/verify-phase-1.sh

printf '\nPhase 2 verification passed.\n'