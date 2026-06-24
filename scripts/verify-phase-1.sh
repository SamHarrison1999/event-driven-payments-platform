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
  ".github/workflows/ci.yml"
  ".env.example"
  "compose.yaml"
  "README.md"
  "backend/build.gradle.kts"
  "backend/settings.gradle.kts"
  "backend/gradlew"
  "backend/src/main/resources/application.yml"
  "backend/src/main/resources/db/migration/V1__establish_schema_baseline.sql"
  "backend/src/test/java/com/samharrison/payments/BackendIntegrationTest.java"
  "backend/src/test/java/com/samharrison/payments/ModularityTest.java"
  "frontend/package.json"
  "frontend/pnpm-lock.yaml"
  "frontend/src/App.tsx"
  "frontend/src/App.test.tsx"
  "frontend/src/features/system/api/getSystemInfo.test.ts"
  "frontend/src/features/system/api/getSystemInfo.ts"
  "frontend/vite.config.ts"
  "scripts/verify-phase-0.sh"
  "scripts/verify-phase-1.ps1"
  "scripts/verify-phase-1.sh"
)

fail() {
  printf 'Phase 1 verification failed: %s\n' "$1" >&2
  exit 1
}

cd "${REPOSITORY_ROOT}"

printf '==> Check required Phase 1 files\n'

for file in "${REQUIRED_FILES[@]}"; do
  if [[ ! -f "${file}" ]]; then
    fail "required file is missing: ${file}"
  fi

  if [[ ! -s "${file}" ]]; then
    fail "required file is empty: ${file}"
  fi
done

if ! grep -Fqi \
  "this application does not process real money" \
  README.md; then
  fail "README does not contain the educational-use warning"
fi

printf '\n==> Validate Docker Compose configuration\n'
docker compose config

printf '\n==> Verify Phase 0 foundation\n'
bash scripts/verify-phase-0.sh

printf '\n==> Test and package backend\n'
(
  cd backend
  ./gradlew clean test bootJar --no-daemon
)

printf '\n==> Install and verify frontend\n'
(
  cd frontend
  pnpm install --frozen-lockfile
  pnpm lint
  pnpm test
  pnpm build
)

printf '\n==> Check unstaged whitespace\n'
git diff --check

printf '\n==> Check staged whitespace\n'
git diff --cached --check

printf '\nPhase 1 verification passed.\n'
