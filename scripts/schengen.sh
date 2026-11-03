#!/usr/bin/env bash
#
# Schengen 90/180 day count from the newest JitterTravel backup JSON.
#
# Usage:
#   scripts/schengen.sh                        # newest jittertravel-backup*.json in ~/Downloads
#   scripts/schengen.sh --today 2026-12-01     # extra flags go straight to schengen_days.py
#   BACKUP_DIR=~/backups scripts/schengen.sh   # look somewhere else
#
# "Newest" is by the timestamp in the file name (…-2026-11-03T230918Z.json), so the one
# you exported last wins even if an older one was re-downloaded later. A plain
# jittertravel-backup.json (no stamp) is ignored.

set -euo pipefail

BACKUP_DIR="${BACKUP_DIR:-$HOME/Downloads}"
here="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

die() { printf 'error: %s\n' "$1" >&2; exit 1; }

# Stamped names sort chronologically as text, so the lexically last is the newest.
latest="$(ls -1 "$BACKUP_DIR"/jittertravel-backup*-20[0-9][0-9]-*.json 2>/dev/null | sort | tail -n 1)"
[[ -n "$latest" ]] || die "no jittertravel-backup*-<timestamp>.json in $BACKUP_DIR — export one from /admin/backup first"

stamp="$(basename "$latest" .json)"
stamp="${stamp##*-20}"            # 26-11-03T230918Z
stamp="20${stamp%%T*}"            # 2026-11-03
if [[ "$stamp" < "$(date -u +%Y-%m-%d)" ]]; then
  printf 'note: newest backup is from %s — export a fresh one from /admin/backup if anything was booked since.\n\n' "$stamp"
fi

exec python3 -I "$here/schengen_days.py" "$latest" "$@"
