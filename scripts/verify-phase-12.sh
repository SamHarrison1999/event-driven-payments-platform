#!/usr/bin/env bash
set -euo pipefail

repo_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
static_only="${1:-}"

required_files=(
  "docs/adr/0016-security-hardening-and-threat-model.md"
  "docs/security/threat-model.md"
  "backend/src/main/java/com/samharrison/payments/shared/config/SecurityRateLimitProperties.java"
  "backend/src/main/java/com/samharrison/payments/shared/infrastructure/web/FixedWindowRateLimiter.java"
  "backend/src/main/java/com/samharrison/payments/shared/infrastructure/web/SecurityHeadersFilter.java"
  "backend/src/main/java/com/samharrison/payments/shared/infrastructure/web/SecurityRateLimitingFilter.java"
  "backend/src/test/java/com/samharrison/payments/shared/infrastructure/web/FixedWindowRateLimiterTest.java"
  "backend/src/test/java/com/samharrison/payments/shared/infrastructure/web/SecurityHeadersFilterTest.java"
  "backend/src/test/java/com/samharrison/payments/shared/infrastructure/web/SecurityRateLimitingFilterTest.java"
  "backend/src/test/java/com/samharrison/payments/shared/infrastructure/web/RequestCompletionLoggingFilterTest.java"
)

for required_file in "${required_files[@]}"; do
  test -f "$repo_root/$required_file"
done

grep -Fq 'max-file-size: 1MB' "$repo_root/backend/src/main/resources/application.yml"
grep -Fq 'max-request-size: 2MB' "$repo_root/backend/src/main/resources/application.yml"
grep -Fq 'SECURITY_RATE_LIMIT_ENABLED:true' "$repo_root/backend/src/main/resources/application.yml"
grep -Fq 'SECURITY_RATE_LIMIT_ENABLED=true' "$repo_root/.env.example"
grep -Fq 'X-Content-Type-Options' "$repo_root/backend/src/main/java/com/samharrison/payments/shared/infrastructure/web/SecurityHeadersFilter.java"
grep -Fq 'Retry-After' "$repo_root/backend/src/main/java/com/samharrison/payments/shared/infrastructure/web/SecurityRateLimitingFilter.java"
grep -Fq 'does not process real money' "$repo_root/SECURITY.md"

if grep -RInE \
  --exclude='*.lock' \
  --exclude='*.md' \
  --exclude='*.map' \
  -e 'BEGIN (RSA|OPENSSH|EC|DSA) PRIVATE KEY' \
  -e 'AKIA[0-9A-Z]{16}' \
  -e 'Authorization:[[:space:]]*Bearer[[:space:]]+[A-Za-z0-9._-]{20,}' \
  "$repo_root/backend/src" "$repo_root/frontend/src"; then
  echo 'Potential hard-coded credential found.' >&2
  exit 1
fi

git -C "$repo_root" diff --check

if [[ "$static_only" != "--static-only" ]]; then
  task_gradle_home="${GRADLE_USER_HOME:-$repo_root/.gradle-phase-12}"
  GRADLE_USER_HOME="$task_gradle_home" \
    "$repo_root/backend/gradlew" clean test bootJar --no-daemon

  corepack pnpm --dir "$repo_root/frontend" install --frozen-lockfile
  corepack pnpm --dir "$repo_root/frontend" lint
  corepack pnpm --dir "$repo_root/frontend" test
  corepack pnpm --dir "$repo_root/frontend" build
fi

echo 'Phase 12 static verification passed.'
