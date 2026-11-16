#!/usr/bin/env bash
# Checks the line coverage that JaCoCo measured against the badge of the README.
#
# Usage, after "./mvnw verify":
#   bash scripts/check-coverage.sh
#
# It prints the measured percentage and fails in three cases, each with a message that says
# what to correct:
#   1. the report is missing;
#   2. the coverage is below the minimum;
#   3. the number of the README badge is not the measured one, rounded down.
#
# On GitHub Actions it also writes the percentage in the summary of the run. It needs only
# bash, awk and grep. The decision is in docs/adr/0021-visible-test-coverage.md.
set -euo pipefail
export LC_ALL=C

# Lowest line coverage accepted, as a whole percentage.
MINIMUM=95

root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
report="$root/target/site/jacoco/jacoco.csv"
readme="$root/README.md"
# The badge is a fixed shields.io image, with the number written in its address.
badge='img\.shields\.io/badge/cobertura-[0-9]+%25'

# Writes a line in the summary of the run. Outside GitHub Actions it does nothing.
summary() {
    if [ -n "${GITHUB_STEP_SUMMARY:-}" ]; then
        echo "$1" >> "$GITHUB_STEP_SUMMARY"
    fi
}

# Reports a failed check, here and in the summary of the run, and lets the script go on, so
# that one run shows everything there is to correct.
failed=0
fail() {
    echo "ERROR: $1" >&2
    echo "       $2" >&2
    summary "**Failed:** $1 $2"
    failed=1
}

if [ ! -f "$report" ]; then
    fail "the coverage report is missing: target/site/jacoco/jacoco.csv." \
        "Run ./mvnw verify first, which writes it, and then this script again."
    exit 1
fi

# The report has one row per class. The two columns are found by their names in the header;
# without them, the total stays at zero.
read -r covered total < <(awk -F, '
    NR == 1 {
        for (i = 1; i <= NF; i++) {
            if ($i == "LINE_MISSED") m = i
            if ($i == "LINE_COVERED") c = i
        }
        next
    }
    m && c { missed += $m; covered += $c }
    END { print covered + 0, covered + missed }' "$report")

if [ "$total" -eq 0 ]; then
    fail "the coverage report counts no lines: target/site/jacoco/jacoco.csv." \
        "Run ./mvnw verify again, which rewrites it, and then this script."
    exit 1
fi

# Whole arithmetic only. The badge shows the percentage rounded down, so it never says more
# than what was measured. The two decimals are for the output: rounded to the nearest, but
# never up to the next whole number, so that 94.996 % is not printed as 95.00 %.
rounded_down=$(( covered * 100 / total ))
hundredths=$(( (covered * 10000 + total / 2) / total ))
if [ $(( hundredths / 100 )) -gt "$rounded_down" ]; then
    hundredths=$(( rounded_down * 100 + 99 ))
fi
percentage="$(( hundredths / 100 )).$(printf '%02d' $(( hundredths % 100 )))"

echo "Line coverage: $percentage % ($covered of $total lines)"
summary "**Line coverage: $percentage %** ($covered of $total lines)"

if [ "$rounded_down" -lt "$MINIMUM" ]; then
    fail "the line coverage is $percentage %, below the minimum of $MINIMUM %." \
        "Add tests for the lines without coverage: target/site/jacoco/index.html shows them."
fi

badges="$(grep -oE "$badge" "$readme" || true)"
count="$(printf '%s' "$badges" | grep -c . || true)"
if [ "$count" -ne 1 ]; then
    fail "README.md has $count coverage badges, and it must have exactly one." \
        "Leave a single one, with this address: https://img.shields.io/badge/cobertura-$rounded_down%25-brightgreen"
else
    written="${badges##*cobertura-}"
    written="${written%\%25}"
    if [ "$written" != "$rounded_down" ]; then
        fail "the README badge says $written %, and the measured coverage is $rounded_down % ($percentage %, rounded down)." \
            "Write $rounded_down in the badge of README.md: .../badge/cobertura-$rounded_down%25-..."
    fi
fi

if [ "$failed" -ne 0 ]; then
    exit 1
fi
echo "The README badge says $rounded_down %, and the minimum is $MINIMUM %: all in order."
