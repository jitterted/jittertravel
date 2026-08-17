#!/usr/bin/env bash
# MUST-PASS gate (Ted, 2026-08-17): block `git push` unless BOTH test suites are green.
# "All tests" is both tiers because the js tier is opt-in and excluded from the default
# build — commit 0435623 broke ConfirmedCalendarToggleJsTest and shipped because the js
# tier is not in `./mvnw test`. A green default build is NOT proof the push is safe.
#
# Wired as a PreToolUse hook on Bash, filtered with  if: "Bash(git push*)"  so it only
# runs on push, never on every shell command. Emits a PreToolUse "deny" decision (exit 0)
# when either suite fails; silent exit 0 = allow.
set -uo pipefail

deny() {
  # $1 = reason shown to the model / user
  printf '{"hookSpecificOutput":{"hookEventName":"PreToolUse","permissionDecision":"deny","permissionDecisionReason":%s}}\n' \
    "$(printf '%s' "$1" | python3 -c 'import json,sys; print(json.dumps(sys.stdin.read()))')"
  exit 0
}

cd "${CLAUDE_PROJECT_DIR:-.}" || deny "pre-push gate: could not enter project directory"

if ./mvnw -q test && ./mvnw -q test -Pjs-tests; then
  exit 0   # both green -> allow the push
fi

deny "MUST-PASS gate: tests failed, so the push is blocked. Run './mvnw test' AND './mvnw test -Pjs-tests', fix every failure, then push again."
