#!/usr/bin/env bash
# Runs a UI test of this folder in a simulator that's booted, building the tests when their
# source changed. A value that a test reads goes in its environment as TEST_RUNNER_<NAME>.
#
#   ios/feel/run.sh UDID Class[/test]
set -euo pipefail

udid=$1
test=$2
cd "$(dirname "$0")/../.."

# the project is made next to a copy of its source, where XcodeGen looks for it
built=ios/build/feel
mkdir -p "$built"
changed=
for source in ios/feel/*.swift ios/feel/project.yml; do
    cmp -s "$source" "$built/$(basename "$source")" || changed=yes
done
if [ -n "$changed" ] || [ ! -d "$built/derived" ]; then
    cp ios/feel/*.swift ios/feel/project.yml "$built/"
    (cd "$built" && xcodegen generate > /dev/null)
    xcodebuild build-for-testing -project "$built/Feel.xcodeproj" -scheme Feel -destination "id=$udid" -derivedDataPath "$built/derived" > "$built/build.log" 2>&1 ||
        { echo "The UI tests didn't build, see $built/build.log" >&2; exit 1; }
fi
xcodebuild test-without-building -project "$built/Feel.xcodeproj" -scheme Feel -destination "id=$udid" -derivedDataPath "$built/derived" \
    "-only-testing:Feel/$test" > "$built/test.log" 2>&1 &
running=$!
# xcodebuild sometimes stays for minutes after its tests ended, so what it wrote is what's waited for
for _ in $(seq 1 600); do
    kill -0 "$running" 2> /dev/null || break
    if grep -Eq "^Test Suite 'Selected tests' (passed|failed)" "$built/test.log"; then
        sleep 2
        kill "$running" 2> /dev/null || true
        break
    fi
    sleep 1
done
kill "$running" 2> /dev/null || true
wait "$running" 2> /dev/null || true
grep -q "^Test Suite 'Selected tests' passed" "$built/test.log" || { echo "The UI test $test failed, see $built/test.log" >&2; exit 1; }
# a test that isn't in the project passes too, without having run
grep -Eq "Executed [1-9][0-9]* tests?" "$built/test.log" || { echo "No UI test is called $test, see $built/test.log" >&2; exit 1; }
