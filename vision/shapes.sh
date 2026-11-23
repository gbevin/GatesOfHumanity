#!/usr/bin/env bash
# Shows the chart in the room as the site describes it now, without building the native
# library: the site on the JVM writes the shapes of a chart, and the app that vision/build.sh
# installed in the simulator shows them from that file, from up close, and taps one. It
# leaves a picture of the simulator, and the simulator booted for the next look: shut it down
# after the last one.
#
#   vision/shapes.sh OUT.png [address after /wheel/volume] [shape to tap]
#
# By default the chart of a birth, /birth/197506211430/2643743, with nothing tapped.
set -euo pipefail

if [ $# -lt 1 ]; then
    echo "Usage: $0 OUT.png [address after /wheel/volume] [shape to tap]" >&2
    exit 2
fi
out=$(cd "$(dirname "$1")" && pwd)/$(basename "$1")
chart=${2:-/birth/197506211430/2643743}
tap=${3:-1}
cd "$(dirname "$0")/.."
udid=$(./bld app-simulator vision-sim "Apple Vision Pro")
folder=$(mktemp -d)
trap 'rm -rf "$folder"' EXIT

./bld compile > /dev/null
# RIFE2 answers the address as the app's library does, and keeps the answer as a file
java -Drife.nativeapp.saved="$folder" -cp "build/main:src/main/resources:$(ls lib/compile/*.jar lib/standalone/*.jar | tr '\n' ':')" \
    rife.nativeapp.NativeApp "/wheel/volume$chart" > /dev/null
shapes=$(ls "$folder"/* | head -1)

xcrun simctl bootstatus "$udid" -b > /dev/null
SIMCTL_CHILD_GATES_VOLUME="$tap" SIMCTL_CHILD_GATES_SHAPES="$shapes" \
    perl -e 'alarm 40; exec @ARGV' xcrun simctl launch --console-pty --terminate-running-process "$udid" com.uwyn.gatesofhumanity > "$folder/told" 2>&1 &
# until the app said that the chart is built, and it's drawn
for _ in $(seq 1 60); do
    grep -a -q "^volume [0-9]" "$folder/told" 2> /dev/null && break
    perl -e 'select(undef, undef, undef, 0.5)'
done
perl -e 'select(undef, undef, undef, 4)'
xcrun simctl io "$udid" screenshot "$out" > /dev/null 2>&1
grep -a "^volume [0-9]" "$folder/told" | cut -c1-80 || { echo "The app didn't build the chart in the room" >&2; exit 1; }
echo "$out, shut the simulator down with: xcrun simctl shutdown $udid"
