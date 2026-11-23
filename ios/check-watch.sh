#!/usr/bin/env bash
# Checks what the app on the iPhone makes for the watch app and what the watch app does with
# it, in the simulators of an iPhone and of its watch, where it builds and installs both apps
# first. The iPhone app writes what it would send to a file, and the watch app takes that file
# as if it was sent and says what it kept: a screen for the transits, one for the body graph
# and one for each of the saved people, each with its drawings for the days ahead and with
# what a tap on it opens, what a watch face shows in three sizes, the rows that a tap on the
# transits opens with what explains the first one, and the words of what's on the screens now. The check hands the
# file over itself, since two simulators take minutes to pass something on, when they do. It
# has the watch app begin at what explains the second screen and tap a watch face then, which
# opens the screen of the transits: its picture is left in ios/build/sim/watch.png to look at.
#
#   ios/check-watch.sh [simulator name of the iPhone]     "iPhone 17" by default
set -euo pipefail

device=${1:-iPhone 17}
cd "$(dirname "$0")/.."
. ios/watch/simulators.sh
folder=$(mktemp -d)
trap 'rm -rf "$folder"' EXIT
(ios/build.sh "$device" && ios/build-watch.sh "$device") > "$folder/built" 2>&1 || { cat "$folder/built" >&2; exit 1; }

app=com.uwyn.gatesofhumanity.watchkitapp
SIMCTL_CHILD_GATES_WATCH="$folder/drawings" xcrun simctl launch --terminate-running-process "$phone" com.uwyn.gatesofhumanity > /dev/null
for _ in $(seq 60); do
    [ -f "$folder/drawings" ] && break
    python3 -c "import time; time.sleep(1)"
done
if [ ! -f "$folder/drawings" ]; then
    echo "The iPhone app made nothing for the watch" >&2
    exit 1
fi
# the app begins past a tap on a row of the second screen, and a watch face is tapped then
SIMCTL_CHILD_GATES_DRAWINGS="$folder/drawings" SIMCTL_CHILD_GATES_SCREEN=1 SIMCTL_CHILD_GATES_DETAIL=1 SIMCTL_CHILD_GATES_ROW=0 SIMCTL_CHILD_GATES_FACE=4 \
    perl -e 'alarm 60; exec @ARGV' xcrun simctl launch --console-pty --terminate-running-process "$watch" $app > "$folder/told" 2>&1 &
for _ in $(seq 40); do
    grep -aq "^faced " "$folder/told" && break
    python3 -c "import time; time.sleep(1)"
done
python3 -c "import time; time.sleep(3)"
xcrun simctl io "$watch" screenshot ios/build/sim/watch.png > /dev/null 2>&1 || true
xcrun simctl terminate "$watch" $app > /dev/null 2>&1 || true
kept=$(grep -a "^kept " "$folder/told" | head -1 | tr -d '\r' || true)
faced=$(grep -a "^faced " "$folder/told" | head -1 | tr -d '\r' || true)
if [ "$faced" != "faced Transits" ]; then
    echo "A tap on a watch face didn't open the screen of the transits, the watch app says: ${faced:-nothing}" >&2
    exit 1
fi
if [[ "$kept" =~ ^kept\ Transits\ [0-9]{2,}\+[0-9]{2,},\ Body\ Graph\ [1-9][0-9]*\+[1-9][0-9]*(,\ [^,\;]+\ [1-9][0-9]*\+[1-9][0-9]*)*\;\ accessoryCircular\ accessoryInline\ accessoryRectangular\;\ 13\ rows,\ Sun\ Sun,\ Gate\ [0-9]+,\ [^:]+,\ Line\ [1-6]:\ The\ Sun\ carries[^\;]*\;\ SUN\ +[0-9]+\.[1-6]\  ]]; then
    # a person's screen has their name as its title, so their drawing doesn't say it again
    people=${kept%%;*}
    people=${people#*Body Graph }
    words=$(printf '%s' "${kept##*; }" | tr '[:lower:]' '[:upper:]')
    while IFS= read -r person; do
        name=$(printf '%s' "${person% *}" | tr '[:lower:]' '[:upper:]')
        if [ -n "$name" ] && [[ "$words" == *" $name"* ]]; then
            echo "The drawing of ${person% *} says the name that its screen has for a title: ${kept##*; }" >&2
            exit 1
        fi
    done < <(printf '%s\n' "$people" | tr ',' '\n' | sed -n '2,$s/^ //p')
    echo "The watch app ${kept%%;*} of what the iPhone app made, with what a watch face shows in three sizes"
else
    echo "The watch app didn't keep what the iPhone app made for it, it says: ${kept:-nothing}" >&2
    exit 1
fi
