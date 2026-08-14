"""Deny in-place file edits made through the shell.

Claude has Edit/Write built in: they show a diff, are checkpointed for /rewind,
and cannot silently mangle a file with a bad regex. Shell text-manglers
(sed -i, perl -pi -e, ed, python3 -c 'open(...,"w")') do none of that, and each
new invocation is a fresh permission prompt because the allow rules are matched
by command prefix. This hook closes that path so there is exactly one way to
edit a file.

Read-only uses are deliberately left alone: `sed 's/x/y/' f`, `perl -ne`,
`awk '{print $1}'` in a pipe all still work. Only in-place mutation is denied.
"""

import json
import os
import re
import shlex
import sys

# Shell operators that separate one command from the next.
SEGMENT_SPLIT = re.compile(r"\|\||&&|[|;\n]")

STREAM_EDITORS = ("sed", "gsed", "perl")
FILE_EDITORS = ("ed", "ex")
INTERPRETERS = ("python", "python3")

# A perl/sed in-place flag cluster: -i, -i.bak, -pi, -0pi, --in-place=.bak
IN_PLACE_CLUSTER = re.compile(r"^-[0-9pnalswFE]*i")

PYTHON_WRITE = re.compile(
    r"""open\s*\([^)]*['"][rwax+]*[wax]\+?['"]|\.write(_text|_bytes)?\s*\(|"""
    r"""Path\s*\([^)]*\)\s*\.\s*write_|shutil\.(copy|move)""",
    re.VERBOSE,
)

USE_EDIT = (
    "Use the Edit tool instead (or Write for a whole new file). It is already "
    "permitted here, shows a diff, and is checkpointed for /rewind."
)


def in_place_flag(tokens):
    """True if any flag token asks sed/perl to rewrite the file in place."""
    for token in tokens[1:]:
        if token == "--":
            break
        if token.startswith("--"):
            if token.startswith("--in-place"):
                return True
            continue
        if token.startswith("-") and IN_PLACE_CLUSTER.match(token):
            return True
    return False


def violation(command):
    """Return a human-readable reason to deny, or None to allow."""
    for segment in SEGMENT_SPLIT.split(command):
        segment = segment.strip()
        if not segment:
            continue
        try:
            tokens = shlex.split(segment)
        except ValueError:
            continue  # unbalanced quotes: not our call to make
        # Scan every position, not just the first: `... | xargs sed -i` and
        # `env perl -pi -e` both hide the real program behind a wrapper.
        for index, token in enumerate(tokens):
            program = os.path.basename(token)
            rest = tokens[index:]
            if program in STREAM_EDITORS and in_place_flag(rest):
                return f"`{program}` with an in-place flag rewrites the file directly."
            if program in FILE_EDITORS and index == 0:
                return f"`{program}` is a file editor."
            if program in INTERPRETERS and "-c" in rest and PYTHON_WRITE.search(segment):
                return f"this `{program} -c` writes to a file."
    return None


def main():
    try:
        payload = json.load(sys.stdin)
    except (json.JSONDecodeError, ValueError):
        return  # malformed input: stay out of the way

    command = payload.get("tool_input", {}).get("command", "")
    if not command:
        return

    reason = violation(command)
    if reason is None:
        return

    json.dump(
        {
            "hookSpecificOutput": {
                "hookEventName": "PreToolUse",
                "permissionDecision": "deny",
                "permissionDecisionReason": f"Shell edits are disabled: {reason} {USE_EDIT}",
            }
        },
        sys.stdout,
    )


if __name__ == "__main__":
    main()
