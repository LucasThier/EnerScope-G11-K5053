#!/usr/bin/env bash
# Static checks on the Flyway migrations. No database needed; the migrations are
# actually run against PostgreSQL by backend/src/test/.../MigrationsOnPostgresTest.
#
#   scripts/check-migrations.sh                  names and unique versions
#   scripts/check-migrations.sh origin/master    ...and what this branch changes
#
# With a base ref (CI passes the pull request's target branch) it also fails if
# the branch edits, deletes or renames a migration that the base already has, and
# if a migration it adds is not named V<yyyyMMddHHmm>__<description>.sql.
#
# Why: sequential numbers are picked independently on every branch, so two
# branches both take "the next one" and the loser has to rename a file that may
# already have run on somebody's database. A timestamp does not collide.
# Migrations that reached the base are applied on other databases and Flyway
# rejects a changed checksum, which is why they must not be edited.
#
# ALLOW_MIGRATION_EDITS=1 turns the "do not touch existing migrations" errors
# into warnings, for the rare change to a migration that never ran anywhere. CI
# sets it when the pull request carries the `migration-edit-ok` label.

set -euo pipefail

MIG_DIR="backend/src/main/resources/db/migration"
BASE_REF="${1:-}"
ALLOW_EDITS="${ALLOW_MIGRATION_EDITS:-}"

cd "$(git rev-parse --show-toplevel)"

if [[ ! -d "$MIG_DIR" ]]; then
  echo "No migration directory at $MIG_DIR - nothing to check."
  exit 0
fi

fail=0
error() { echo "::error file=$1::$2"; fail=1; }
warn()  { echo "::warning file=$1::$2"; }

# Flyway compares versions part by part as numbers, so 8, 08, 8.0 and 8_0 are the
# same version. Reduce them to one spelling before looking for duplicates.
normalise() {
  local v="${1//_/.}" part
  local -a parts out=()
  IFS='.' read -ra parts <<< "$v"
  for part in "${parts[@]}"; do out+=("$((10#$part))"); done
  while (( ${#out[@]} > 1 )) && [[ "${out[${#out[@]}-1]}" == 0 ]]; do
    unset "out[${#out[@]}-1]"
  done
  (IFS=.; echo "${out[*]}")
}

# --- 1. every file is well named, and no two share a version -----------------
declare -A seen=()
shopt -s nullglob
for f in "$MIG_DIR"/*.sql; do
  base=$(basename "$f")

  case "$base" in
    R__*) continue ;;   # repeatable migrations carry no version
  esac

  if [[ ! "$base" =~ ^V[0-9]+([._][0-9]+)*__.+\.sql$ ]]; then
    error "$f" "'$base' does not follow V<version>__<description>.sql (two underscores before the description). Flyway silently ignores a file like this, so it would never run."
    continue
  fi

  ver="${base#V}"
  ver="${ver%%__*}"
  norm=$(normalise "$ver")

  if [[ -n "${seen[$norm]:-}" ]]; then
    error "$f" "Duplicate migration version '$ver': '$base' collides with '${seen[$norm]}'. Rename the one from your branch to a fresh timestamp: V$(date -u +%Y%m%d%H%M)__<description>.sql"
  else
    seen[$norm]="$base"
  fi
done

# --- 2. what this branch does to the migrations the base already has ---------
if [[ -n "$BASE_REF" ]]; then
  if ! git rev-parse --verify --quiet "$BASE_REF^{commit}" > /dev/null; then
    echo "::error::Cannot resolve base ref '$BASE_REF' (CI must check out the full history: fetch-depth: 0)."
    exit 1
  fi

  # Three dots: changes on this branch since it left the base, not whatever the
  # base gained afterwards.
  while IFS=$'\t' read -r status path new_path; do
    [[ -z "${status:-}" ]] && continue
    case "${status:0:1}" in
      A)
        file=$(basename "$path")
        case "$file" in R__*) continue ;; esac
        if [[ ! "$file" =~ ^V[0-9]{12}__.+\.sql$ ]]; then
          error "$path" "New migrations are named V<yyyyMMddHHmm>__<description>.sql, e.g. V$(date -u +%Y%m%d%H%M)__${file#*__}. A sequential number collides with whatever the other branches picked at the same time."
        fi
        ;;
      M|D|R)
        case "${status:0:1}" in
          M) what="modified" ;;
          D) what="deleted" ;;
          R) what="renamed to '$(basename "$new_path")'" ;;
        esac
        msg="'$(basename "$path")' already exists on $BASE_REF and was $what here. Databases that ran it reject a changed checksum; add a new migration instead. If it never ran anywhere, label the pull request 'migration-edit-ok'."
        if [[ -n "$ALLOW_EDITS" ]]; then warn "$path" "$msg"; else error "$path" "$msg"; fi
        ;;
    esac
  done < <(git diff --name-status -M "$BASE_REF"...HEAD -- "$MIG_DIR")
fi

if [[ $fail -ne 0 ]]; then
  echo ""
  echo "Migration check failed."
  exit 1
fi

echo "Migration check passed (${#seen[@]} versioned migrations):"
for f in "$MIG_DIR"/*.sql; do echo "  - $(basename "$f")"; done
