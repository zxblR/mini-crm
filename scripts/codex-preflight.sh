#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT_DIR"

current_branch="$(git branch --show-current)"
if [[ "$current_branch" != "feature" ]]; then
  printf 'codex-preflight: expected branch feature, got %s\n' "${current_branch:-detached}" >&2
  exit 1
fi

unexpected_branches="$(
  git for-each-ref --format='%(refname:short)' refs/heads |
    while IFS= read -r branch; do
      case "$branch" in
        main|feature) ;;
        *) printf '%s\n' "$branch" ;;
      esac
    done
)"
if [[ -n "$unexpected_branches" ]]; then
  printf 'codex-preflight: unsupported local branches:\n%s\n' "$unexpected_branches" >&2
  exit 1
fi

required_paths=(
  "apps/web"
  "apps/api/pom.xml"
  "apps/ai"
  "packages/shared"
  "prisma/schema.prisma"
  "prisma/migrations"
  "infra/docker"
  "docs"
  "scripts"
)

for path in "${required_paths[@]}"; do
  if [[ ! -e "$path" ]]; then
    printf 'codex-preflight: missing %s\n' "$path" >&2
    exit 1
  fi
done

printf '%s\n' "codex-preflight: branch and directory checks passed"

mvn -f apps/api/pom.xml test
pnpm -C apps/web lint
pnpm -C apps/web build
python -m compileall apps/ai
docker compose config

printf '%s\n' "codex-preflight: acceptance checks passed"
