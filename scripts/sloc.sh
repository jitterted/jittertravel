#!/usr/bin/env bash
#
# Source lines of code, production vs test, broken down by package.
#
# "Code" is a line that is not blank and not a comment. A comment is a line
# starting with //, or any line of a /* ... */ block (Javadoc included). It is an
# approximation, not a parser: a comment marker inside a string or a text block
# is taken at face value. Good enough for proportions and trends, not for an
# exact figure.
#
# Usage:
#   scripts/sloc.sh            # run from anywhere; it finds the repo root itself
#
# Needs only bash, find and the system awk. Nothing to install.

set -euo pipefail

cd "$(dirname "$0")/.."

BASE="dev/ted/jittertravel"

# One line per Java file: tier <TAB> package <TAB> code comment blank
per_file() {
  find src/main/java src/test/java -type f -name '*.java' -print0 |
    xargs -0 awk -v base="$BASE" '
      function flush() {
        if (file == "") return
        printf "%s\t%s\t%d %d %d\n", tier, pkg, code, cmt, blank
      }
      FNR == 1 {
        flush()
        file = FILENAME; code = cmt = blank = 0; inblock = 0
        tier = (file ~ /^src\/main\//) ? "main" : "test"
        pkg = file
        sub("^src/(main|test)/java/" base "/?", "", pkg)
        if (pkg ~ /\//) { sub(/\/[^\/]*$/, "", pkg); gsub(/\//, ".", pkg) } else pkg = "(root)"
      }
      {
        line = $0; sub(/^[ \t]+/, "", line)
        if (inblock) {
          if (line ~ /\*\//) {
            inblock = 0; rest = line; sub(/.*\*\//, "", rest)
            if (rest ~ /[^ \t]/) code++; else cmt++
          } else cmt++
          next
        }
        if (line == "") { blank++; next }
        if (line ~ /^\/\//) { cmt++; next }
        if (line ~ /^\/\*/) { if (line !~ /\*\//) inblock = 1; cmt++; next }
        code++
      }
      END { flush() }'
}

# awk cannot sort portably, so the table is three steps: aggregate, sort, print.
per_file | awk -F'\t' '
  {
    split($3, n, " ")
    files[$1, $2]++; code[$1, $2] += n[1]; cmt[$1, $2] += n[2]; pkgs[$2] = 1
  }
  END {
    for (p in pkgs)
      printf "%s\t%d\t%d\t%d\t%d\t%d\t%d\n", p,
        files["main", p], code["main", p], cmt["main", p],
        files["test", p], code["test", p], cmt["test", p]
  }' | sort | awk -F'\t' '
  function ratio(t, m) { return m ? sprintf("%.1f", t / m) : "-" }
  BEGIN {
    fmt = "%-16s %6s %8s %8s   %6s %8s %8s   %6s\n"
    printf "Java source lines by package (dev.ted.jittertravel.*)\n\n"
    printf fmt, "", "main", "", "", "test", "", "", "test:"
    printf fmt, "package", "files", "code", "comment", "files", "code", "comment", "main"
    printf fmt, "-------", "-----", "----", "-------", "-----", "----", "-------", "-----"
  }
  {
    printf fmt, $1, $2, $3, $4, $5, $6, $7, ratio($6, $3)
    mf += $2; mc += $3; mm += $4; tf += $5; tc += $6; tm += $7
  }
  END {
    printf fmt, "-------", "-----", "----", "-------", "-----", "----", "-------", "-----"
    printf fmt, "TOTAL", mf, mc, mm, tf, tc, tm, ratio(tc, mc)
    printf "\nTest code is %.0f%% of all Java code.\n", (mc + tc) ? 100 * tc / (mc + tc) : 0
  }'

# Non-Java resources: templates, CSS, SQL, properties. Non-blank lines only,
# since each of these has its own comment syntax.
printf '\nResources (non-blank lines of html/css/js/sql/properties/yml/xml)\n'
for dir in src/main/resources src/test/resources; do
  [ -d "$dir" ] || continue
  lines=$(find "$dir" -type f \( -name '*.html' -o -name '*.css' -o -name '*.js' -o -name '*.sql' \
            -o -name '*.properties' -o -name '*.yml' -o -name '*.yaml' -o -name '*.xml' \) -print0 |
          xargs -0 cat 2>/dev/null | grep -cv '^[[:space:]]*$' || true)
  printf '  %-20s %8s\n' "$dir" "$lines"
done
