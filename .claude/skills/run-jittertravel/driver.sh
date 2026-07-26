#!/bin/bash
# JitterTravel driver — launch and drive the running app as an authenticated user.
#
# The app is a Spring Boot server behind a form-login chain with CSRF on every POST,
# so you cannot drive it with bare curl: you need a cookie jar and a per-page token.
# This script handles both.
#
#   ./driver.sh start                       # launch app + Postgres, wait until UP
#   ./driver.sh login [user] [pass]         # default: ted / preview  (OWNER role)
#   ./driver.sh get  /planned-gatherings    # authenticated GET, prints body
#   ./driver.sh post /plan-gathering title=LJC city=London date=2026-09-15 ...
#   ./driver.sh sql  "select * from event_log"
#   ./driver.sh stop
#
# `post` fetches a fresh CSRF token from the target page, then submits it with the
# form fields. It prints the status line and follows nothing — read the redirect.
#
# Requires: the app checked out at UNIT_DIR (default: this skill's repo root), Docker
# running (Postgres is auto-started from compose.yml by spring-boot-docker-compose).
set -uo pipefail

UNIT_DIR="${UNIT_DIR:-$(cd "$(dirname "${BASH_SOURCE[0]}")/../../.." && pwd)}"
BASE="${BASE:-http://localhost:8080}"
WORK="${WORK:-${TMPDIR:-/tmp}/jittertravel-driver}"
JAR="$WORK/cookies.txt"
LOG="$WORK/app.log"
PIDFILE="$WORK/app.pid"

mkdir -p "$WORK"

# Extract the _csrf token from a page. Attribute order differs between Spring's
# generated login page and our Thymeleaf forms, so match the input tag, not a
# fixed attribute sequence.
csrf() {
  curl -s -b "$JAR" -c "$JAR" "$BASE$1" \
    | grep -o '<input[^>]*name="_csrf"[^>]*>' | head -1 \
    | grep -o 'value="[^"]*"' | sed 's/value="//; s/"//'
}

# Compose names the container <dir>-postgres-1, so the project name follows the
# checkout directory (a git worktree gets its own). The trailing "-1" is what
# distinguishes it from the testcontainers instance, jittertravel-test-postgres.
pg_container() { docker ps -qf "name=postgres-1" | head -1; }

case "${1:-}" in

  start)
    if curl -sf "$BASE/actuator/health" >/dev/null 2>&1; then
      echo "already running at $BASE"; exit 0
    fi
    echo "starting (log: $LOG)"
    ( cd "$UNIT_DIR" && ./mvnw spring-boot:run -Dspring-boot.run.profiles=prod-preview \
        > "$LOG" 2>&1 & echo $! > "$PIDFILE" )
    for _ in $(seq 1 120); do
      if curl -sf "$BASE/actuator/health" >/dev/null 2>&1; then
        echo "UP at $BASE"; exit 0
      fi
      if grep -qE "APPLICATION FAILED TO START|BUILD FAILURE" "$LOG" 2>/dev/null; then
        echo "FAILED to start — tail of $LOG:"; tail -30 "$LOG"; exit 1
      fi
      sleep 2
    done
    echo "timed out waiting for $BASE/actuator/health"; tail -30 "$LOG"; exit 1
    ;;

  stop)
    # spring-boot:run forks a child JVM; kill by port so nothing is left behind.
    pid=$(lsof -ti tcp:8080 2>/dev/null)
    [ -n "$pid" ] && kill $pid && echo "stopped app (pid $pid)" || echo "app not running"
    [ -f "$PIDFILE" ] && kill "$(cat "$PIDFILE")" 2>/dev/null; rm -f "$PIDFILE"
    exit 0
    ;;

  login)
    rm -f "$JAR"
    user="${2:-ted}"; pass="${3:-preview}"
    tok=$(csrf /login)
    [ -z "$tok" ] && { echo "could not read CSRF token from /login — is the app up?"; exit 1; }
    code=$(curl -s -o /dev/null -w '%{http_code}' -b "$JAR" -c "$JAR" -X POST "$BASE/login" \
             -d "username=$user" -d "password=$pass" -d "_csrf=$tok")
    # A failed login also 302s — to /login?error — so verify with a protected page.
    if curl -s -o /dev/null -w '%{redirect_url}' -b "$JAR" -c "$JAR" "$BASE/planned-gatherings" \
         | grep -q login; then
      echo "login FAILED for $user (status $code)"; exit 1
    fi
    echo "logged in as $user"
    exit 0
    ;;

  get)
    curl -s -b "$JAR" -c "$JAR" "$BASE$2"
    exit 0
    ;;

  post)
    path="$2"; shift 2
    tok=$(csrf "$path")
    args=()
    for kv in "$@"; do args+=(--data-urlencode "$kv"); done
    curl -s -o /dev/null -w 'status: %{http_code} -> %{redirect_url}\n' \
      -b "$JAR" -c "$JAR" -X POST "$BASE$path" "${args[@]}" -d "_csrf=$tok"
    exit 0
    ;;

  post-body)
    # Same as post but prints the response body — use when a validation error
    # re-renders the form instead of redirecting.
    path="$2"; shift 2
    tok=$(csrf "$path")
    args=()
    for kv in "$@"; do args+=(--data-urlencode "$kv"); done
    curl -s -b "$JAR" -c "$JAR" -X POST "$BASE$path" "${args[@]}" -d "_csrf=$tok"
    exit 0
    ;;

  sql)
    c=$(pg_container)
    [ -z "$c" ] && { echo "no jittertravel-postgres container running"; exit 1; }
    docker exec "$c" psql -U jitter -d jittertravel -c "$2"
    exit 0
    ;;

  *)
    sed -n '2,25p' "${BASH_SOURCE[0]}" | sed 's/^# \{0,1\}//'
    exit 1
    ;;
esac
