#!/usr/bin/env bash
#
# Manual backup of the production JitterTravel Postgres database.
#
# Railway's own backups need a Pro subscription, so this is the durable backup
# mechanism: a pg_dump taken over Railway's public TCP proxy, run from your
# machine. pg_dump runs inside a Docker container whose Postgres major version
# is detected from the live server, so there is nothing to install locally and
# no "server version mismatch" surprise.
#
# Usage:
#   scripts/backup-db.sh              # dump to ./backups, prune to last 14
#   BACKUP_DIR=/somewhere KEEP=30 scripts/backup-db.sh
#
# Prerequisites:
#   - Railway CLI, logged in, project linked:  railway link
#   - Docker running
#   - jq
#
# Restore is deliberately NOT automated. See DEPLOYMENT.md ("Restoring").

set -euo pipefail

BACKUP_DIR="${BACKUP_DIR:-backups}"
KEEP="${KEEP:-14}"
DB_SERVICE="${DB_SERVICE:-Postgres}"
# Client image used only to ask the server its version; any recent client works.
PROBE_IMAGE="${PROBE_IMAGE:-postgres:18-alpine}"

die() { printf 'error: %s\n' "$1" >&2; exit 1; }

command -v docker >/dev/null || die "docker is not installed"
docker info >/dev/null 2>&1 || die "Docker is not running"

# DB_URL can be supplied directly (handy for dumping a local database, or if you
# already have the proxy URL); otherwise ask Railway for it.
if [[ -z "${DB_URL:-}" ]]; then
  for cmd in railway jq; do
    command -v "$cmd" >/dev/null || die "$cmd is not installed"
  done
  railway status >/dev/null 2>&1 || die "no linked Railway project — run: railway link"

  # The private host (*.railway.internal) is unreachable from a laptop, so the
  # backup goes through the public TCP proxy URL the Postgres service exposes.
  DB_URL="$(railway variables list --service "$DB_SERVICE" --json 2>/dev/null \
    | jq -r '.DATABASE_PUBLIC_URL // empty')"

  if [[ -z "$DB_URL" ]]; then
    die "no DATABASE_PUBLIC_URL on service '$DB_SERVICE'.
  Check the service name (override with DB_SERVICE=...) and confirm a TCP proxy
  exists for it: railway tcp-proxy list --service $DB_SERVICE"
  fi
fi
export DB_URL

# Match the dump client to the server's major version; pg_dump refuses to dump a
# server newer than itself.
server_version="$(docker run --rm -e DB_URL "$PROBE_IMAGE" \
  psql "$DB_URL" -tAX -c 'SHOW server_version' 2>/dev/null | tr -d '[:space:]')" \
  || die "could not connect to Postgres over the public proxy"
[[ -n "$server_version" ]] || die "could not read server_version from Postgres"
server_major="${server_version%%.*}"
dump_image="${DUMP_IMAGE:-postgres:${server_major}-alpine}"

mkdir -p "$BACKUP_DIR"
stamp="$(date -u +%Y%m%d-%H%M%SZ)"
dump_file="$BACKUP_DIR/jittertravel-$stamp.dump"

printf 'Postgres %s → %s (via %s)\n' "$server_version" "$dump_file" "$dump_image"

# -Fc: compressed custom format, restorable whole or table-by-table.
docker run --rm -e DB_URL "$dump_image" \
  pg_dump "$DB_URL" -Fc --no-owner --no-privileges > "$dump_file"

# A dump that can't be listed is not a backup — fail loudly rather than keeping it.
toc="$(docker run --rm -i "$dump_image" pg_restore --list < "$dump_file")" \
  || { rm -f "$dump_file"; die "dump is unreadable — removed $dump_file"; }

for table in command_log event_log; do
  grep -q "TABLE DATA public $table" <<<"$toc" \
    || { rm -f "$dump_file"; die "dump has no data for '$table' — removed $dump_file"; }
done

printf 'ok: %s (%s)\n' "$dump_file" "$(du -h "$dump_file" | cut -f1)"

# Prune oldest, keeping the most recent $KEEP dumps.
# (Plain while-read, not mapfile — macOS ships bash 3.2.)
pruned=0
while IFS= read -r stale; do
  rm -f -- "$stale"
  pruned=$((pruned + 1))
done < <(ls -1t "$BACKUP_DIR"/jittertravel-*.dump 2>/dev/null | tail -n +"$((KEEP + 1))")
if [[ "$pruned" -gt 0 ]]; then
  printf 'pruned %d old backup(s) beyond KEEP=%s\n' "$pruned" "$KEEP"
fi
