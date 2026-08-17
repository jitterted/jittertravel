#!/usr/bin/env bash
# MUST-PASS gate (Ted, 2026-08-17): block `git push` unless BOTH test suites are green.
# "All tests" is both tiers because the js tier is opt-in and excluded from the default
# build — commit 0435623 broke ConfirmedCalendarToggleJsTest and shipped because the js
# tier is not in `./mvnw test`. A green default build is NOT proof the push is safe.
#
# Wired as a PreToolUse hook on Bash (matcher: "Bash"), which fires on EVERY Bash call —
# the matcher matches the tool name, not the command text, and the settings `if:` filter
# proved unreliable at matching a bare `git push`. So this script self-filters: it reads
# the PreToolUse payload from stdin and exits 0 (allow) immediately unless the command is a
# `git push`. Only pushes run the suites. Emits a PreToolUse "deny" decision (exit 0) when
# either suite fails; silent exit 0 = allow.
set -uo pipefail

deny() {
  # $1 = reason shown to the model / user
  printf '{"hookSpecificOutput":{"hookEventName":"PreToolUse","permissionDecision":"deny","permissionDecisionReason":%s}}\n' \
    "$(printf '%s' "$1" | python3 -c 'import json,sys; print(json.dumps(sys.stdin.read()))')"
  exit 0
}

# Read the whole payload before anything else consumes stdin, then extract the command.
# Anything that isn't a `git push` is allowed silently — the gate only guards pushes.
PAYLOAD="$(cat)"
CMD="$(printf '%s' "$PAYLOAD" | python3 -c 'import json,sys; print(json.load(sys.stdin).get("tool_input",{}).get("command",""))' 2>/dev/null || true)"
[[ "$CMD" == *"git push"* ]] || exit 0

cd "${CLAUDE_PROJECT_DIR:-.}" || deny "pre-push gate: could not enter project directory"

if ./mvnw -q test && ./mvnw -q test -Pjs-tests; then
  exit 0   # both green -> allow the push
fi

deny "MUST-PASS gate: tests failed, so the push is blocked. Run './mvnw test' AND './mvnw test -Pjs-tests', fix every failure, then push again."
