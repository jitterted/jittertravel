#!/usr/bin/env bash
#
# Restore a JitterTravel pg_dump (from scripts/backup-db.sh) back into Postgres.
#
# Restore overwrites live family data, so this script is built to make the SAFE
# thing the default and the destructive thing deliberate:
#
#   scripts/restore-db.sh backups/jittertravel-<stamp>.dump
#       → restores into a THROWAWAY scratch database on the same server, prints
#         command_log/event_log row counts so you can eyeball it, and leaves the
#         real database untouched. This is the DEPLOYMENT.md "inspect first" step.
#
#   scripts/restore-db.sh backups/jittertravel-<stamp>.dump --to prod
#       → restores OVER the real database (pg_restore --clean). Refuses unless you
#         type the confirmation phrase, and takes a fresh safety dump of the
#         current prod DB first (via backup-db.sh) before dropping anything.
#
# Like backup-db.sh, every psql/pg_restore runs inside a Docker postgres:<major>
# container whose major version is detected from the live server, so there is
# nothing to install locally and no "server version mismatch" surprise.
#
# Prerequisites (same as backup-db.sh):
#   - Railway CLI, logged in, project linked:  railway link
#   - Docker running
#   - jq
#
# After ANY restore over prod, RESTART the app so the boot replay rebuilds the
# read models; watch the logs for "Replayed N events from persistent store".

set -euo pipefail

DB_SERVICE="${DB_SERVICE:-Postgres}"
SCRATCH_DB="${SCRATCH_DB:-jt_restore_check}"
CONFIRM_PHRASE="restore over production"

die() { printf 'error: %s\n' "$1" >&2; exit 1; }

dump_file=""
target="scratch"
for arg in "$@"; do
  case "$arg" in
    --to) die "use --to=prod or --to=scratch (with an equals sign)" ;;
    --to=prod|--to=production) target="prod" ;;
    --to=scratch) target="scratch" ;;
    --keep-scratch) KEEP_SCRATCH=1 ;;
    -*) die "unknown option: $arg" ;;
    *) [[ -z "$dump_file" ]] || die "more than one dump file given"; dump_file="$arg" ;;
  esac
done

[[ -n "$dump_file" ]] || die "usage: scripts/restore-db.sh <dump-file> [--to=scratch|--to=prod]"
[[ -f "$dump_file" ]] || die "no such dump file: $dump_file"

command -v docker >/dev/null || die "docker is not installed"
docker info >/dev/null 2>&1 || die "Docker is not running"

# DB_URL can be supplied directly (handy for a local target); otherwise ask Railway
# for the public TCP proxy URL — the private *.railway.internal host is unreachable
# from a laptop.
if [[ -z "${DB_URL:-}" ]]; then
  for cmd in railway jq; do
    command -v "$cmd" >/dev/null || die "$cmd is not installed"
  done
  railway status >/dev/null 2>&1 || die "no linked Railway project — run: railway link"
  DB_URL="$(railway variables list --service "$DB_SERVICE" --json 2>/dev/null \
    | jq -r '.DATABASE_PUBLIC_URL // empty')"
  [[ -n "$DB_URL" ]] || die "no DATABASE_PUBLIC_URL on service '$DB_SERVICE' (override with DB_SERVICE=...)"
fi
export DB_URL

# Match the client to the server's major version; pg_restore refuses a server newer than itself.
probe_image="${PROBE_IMAGE:-postgres:18-alpine}"
server_version="$(docker run --rm -e DB_URL "$probe_image" \
  psql "$DB_URL" -tAX -c 'SHOW server_version' 2>/dev/null | tr -d '[:space:]')" \
  || die "could not connect to Postgres over the public proxy"
[[ -n "$server_version" ]] || die "could not read server_version from Postgres"
server_major="${server_version%%.*}"
img="${RESTORE_IMAGE:-postgres:${server_major}-alpine}"

# Derive a URL that points at a different database on the same server (for scratch).
# DB_URL looks like: postgresql://user:pass@host:port/dbname[?query]
rest="${DB_URL#*://}"                 # user:pass@host:port/dbname?query
creds_host="${rest%%/*}"              # user:pass@host:port
after="${rest#*/}"                    # dbname?query  (or dbname)
query=""
[[ "$after" == *\?* ]] && query="?${after#*\?}"
url_for_db() { printf 'postgresql://%s/%s%s' "$creds_host" "$1" "$query"; }

# Sanity-check the dump before touching any database (same contract backup-db.sh enforces).
toc="$(docker run --rm -i "$img" pg_restore --list < "$dump_file")" \
  || die "dump is unreadable: $dump_file"
for table in command_log event_log; do
  grep -q "TABLE DATA public $table" <<<"$toc" \
    || die "dump has no data for '$table' — refusing to restore from it: $dump_file"
done

counts() {  # counts <url>  -> prints "command_log=N event_log=M max_seq=S"
  docker run --rm -e U="$1" "$img" psql "$U" -tAX -c \
    "select 'command_log='||(select count(*) from command_log)
        ||' event_log='||(select count(*) from event_log)
        ||' max_seq='||coalesce((select max(sequence) from event_log)::text,'-')" \
    2>/dev/null | tr -d '[:space:]'
}

if [[ "$target" == "scratch" ]]; then
  scratch_url="$(url_for_db "$SCRATCH_DB")"
  printf 'Postgres %s → SCRATCH db "%s" (real database untouched)\n' "$server_version" "$SCRATCH_DB"
  # Recreate the scratch DB from the server's default connection, then restore into it.
  docker run --rm -e A="$DB_URL" "$img" psql "$A" -v ON_ERROR_STOP=1 \
    -c "DROP DATABASE IF EXISTS ${SCRATCH_DB} WITH (FORCE)" \
    -c "CREATE DATABASE ${SCRATCH_DB}" \
    || die "could not create scratch database ${SCRATCH_DB}"
  docker run --rm -i -e U="$scratch_url" "$img" \
    sh -c 'pg_restore -d "$U" --no-owner --no-privileges' < "$dump_file" \
    || die "restore into scratch failed"
  printf 'ok — scratch restored. Row counts: %s\n' "$(counts "$scratch_url")"
  if [[ -n "${KEEP_SCRATCH:-}" ]]; then
    printf 'scratch DB "%s" kept; inspect with: psql "<proxy-url>/%s"\n' "$SCRATCH_DB" "$SCRATCH_DB"
  else
    docker run --rm -e A="$DB_URL" "$img" psql "$A" -v ON_ERROR_STOP=1 \
      -c "DROP DATABASE IF EXISTS ${SCRATCH_DB} WITH (FORCE)" >/dev/null 2>&1 || true
    printf 'scratch DB dropped (pass --keep-scratch to keep it for inspection).\n'
  fi
  printf '\nLooks right? Restore over prod with:\n  scripts/restore-db.sh %q --to=prod\n' "$dump_file"
  exit 0
fi

# ---- target == prod: destructive, guarded ----
printf '\n*** You are about to OVERWRITE the live production database. ***\n'
printf 'Current prod row counts: %s\n' "$(counts "$DB_URL")"
printf 'Dump to restore:         %s\n' "$dump_file"
printf 'Type exactly: %s\n> ' "$CONFIRM_PHRASE"
read -r reply
[[ "$reply" == "$CONFIRM_PHRASE" ]] || die "confirmation did not match — nothing changed"

# Safety net: dump the CURRENT prod DB before dropping anything. Reuse backup-db.sh
# so the pre-restore dump gets the same verification as any other backup.
script_dir="$(cd "$(dirname "$0")" && pwd)"
printf '\nTaking a safety dump of current prod first...\n'
DB_URL="$DB_URL" "$script_dir/backup-db.sh" || die "safety dump failed — aborting before touching prod"

printf '\nRestoring over prod (pg_restore --clean --if-exists)...\n'
docker run --rm -i -e U="$DB_URL" "$img" \
  sh -c 'pg_restore -d "$U" --clean --if-exists --no-owner --no-privileges' < "$dump_file" \
  || die "restore over prod FAILED — the pre-restore safety dump is in ./backups"

printf 'ok — prod restored. Row counts: %s\n' "$(counts "$DB_URL")"
printf '\nNOW RESTART the app so the boot replay rebuilds the read models.\n'
printf 'Watch the logs for: "Replayed N events from persistent store".\n'
