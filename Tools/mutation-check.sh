#!/usr/bin/env bash
# Applies one mutation to a source file, runs tests against a clean build, reports which tests
# failed and restores the file, even when interrupted.
#
#   Tools/mutation-check.sh <file> <perl substitution> [test class ...]
#   Tools/mutation-check.sh src/main/java/gatesofhumanity/models/Variables.java 's/tone <= 3/tone < 3/' gatesofhumanity.models.VariablesTest
#
# Exits 0 when at least one test fails (the mutation is caught), 1 when every test passes,
# 2 when the mutation can't be applied or tested.
set -uo pipefail

if [[ $# -lt 2 ]]; then
    sed -n '2,9p' "$0" | sed 's/^# \{0,1\}//'
    exit 2
fi
file=$1
substitution=$2
shift 2

cd "$(dirname "$0")/.." || exit 2
if [[ ! -f $file ]]; then
    echo "No such file: $file" >&2
    exit 2
fi

work=$(mktemp -d)
cp "$file" "$work/original"
# a restored file newer than any class compiled from the mutation, so even an incremental build
# after an interruption recompiles it
restore() {
    cp "$work/original" "$file"
    rm -rf "$work"
}
trap restore EXIT
trap 'exit 2' INT TERM

perl -0pi -e "$substitution" "$file" || exit 2
if cmp -s "$file" "$work/original"; then
    echo "The mutation didn't change $file" >&2
    exit 2
fi
diff -u "$work/original" "$file" | sed '1,2d'

# an incremental build can miss stale dependents and hide the mutation
if ! ./bld clean compile >"$work/compile.log" 2>&1; then
    echo "The mutation doesn't compile:" >&2
    head -20 "$work/compile.log" >&2
    exit 2
fi

selection=()
for class in "$@"; do
    selection+=("--select-class=$class")
done
./bld test ${selection[@]+"${selection[@]}"} --reports-dir="$work/reports" >"$work/test.log" 2>&1

python3 -I - "$work/reports/TEST-junit-jupiter.xml" <<'EOF'
import sys
import xml.etree.ElementTree as ET

try:
    cases = ET.parse(sys.argv[1]).getroot().iter("testcase")
except (OSError, ET.ParseError) as e:
    print(f"No test report: {e}", file=sys.stderr)
    sys.exit(2)
ran = 0
failed = []
for case in cases:
    ran += 1
    if case.find("failure") is not None or case.find("error") is not None:
        failed.append(f'{case.get("classname")}.{case.get("name")}')
if ran == 0:
    print("No tests ran", file=sys.stderr)
    sys.exit(2)
if failed:
    print(f"Caught by {len(failed)} of {ran} tests:")
    for name in failed:
        print(f"  {name}")
    sys.exit(0)
print(f"Survived: all {ran} tests pass")
sys.exit(1)
EOF
status=$?

restore
trap - EXIT
# leave a build of the restored source
./bld clean compile >/dev/null 2>&1 || { echo "The restored source doesn't compile" >&2; exit 2; }
exit $status
