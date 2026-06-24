#!/usr/bin/env bash

set -euo pipefail

readonly REQUIRED_FILES=(
  ".editorconfig"
  ".gitattributes"
  ".gitignore"
  "LICENSE"
  "README.md"
  "SECURITY.md"
  "docs/product/scope.md"
  "docs/product/backlog.md"
  "docs/product/definition-of-done.md"
  "docs/architecture/overview.md"
  "docs/adr/0001-modular-monolith.md"
  "docs/adr/0002-technology-baseline.md"
  "docs/adr/0003-money-ledger-and-consistency.md"
  "docs/adr/0004-session-authentication.md"
  "docs/adr/0005-transactional-outbox.md"
  "docs/adr/0006-idempotency-and-payment-lifecycle.md"
  "docs/progress/ledger.md"
  "scripts/verify-phase-0.sh"
)

fail() {
  printf 'Phase 0 verification failed: %s\n' "$1" >&2
  exit 1
}

for file in "${REQUIRED_FILES[@]}"; do
  if [[ ! -f "$file" ]]; then
    fail "required file is missing: $file"
  fi

  if [[ ! -s "$file" ]]; then
    fail "required file is empty: $file"
  fi
done

if ! grep -Fqi \
  "this application does not process real money" \
  README.md; then
  fail "README does not contain the educational-use warning"
fi

if ! grep -Fq \
  "Every ledger transaction has total debits equal to total credits" \
  docs/product/scope.md; then
  fail "ledger invariant is not documented"
fi

if ! grep -Fq \
  "## C4 context diagram" \
  docs/architecture/overview.md; then
  fail "C4 context diagram section is missing"
fi

if ! grep -Fq \
  "## C4 container diagram" \
  docs/architecture/overview.md; then
  fail "C4 container diagram section is missing"
fi

if ! grep -Fq \
  "## Strong consistency boundaries" \
  docs/architecture/overview.md; then
  fail "strong consistency boundaries are not documented"
fi

for adr in docs/adr/*.md; do
  if ! grep -Fq -- "- Status: Accepted" "$adr"; then
    fail "ADR is not marked Accepted: $adr"
  fi
done

if ! grep -Fq \
  "scripts/verify-phase-0.sh" \
  docs/progress/ledger.md; then
  fail "progress ledger does not record the verification gate"
fi

if git rev-parse --is-inside-work-tree >/dev/null 2>&1; then
  if ! git diff --check; then
    fail "git diff --check found whitespace errors"
  fi
fi

printf 'Phase 0 repository checks passed.\n'
