#!/usr/bin/env bash
# Checks the app for visionOS in the simulator, where it builds and installs it first: a page
# marks itself for this app and leaves out what the app has around its window, the chart in
# the room is built out of what the site describes, a tap on a planet of it has the panel
# beside the window explain it and brings the one line from it to its gate, which goes when
# the page explains something else, the chart in the room is the one that the page shows and
# stays on a page without one, the chart of someone else is there at once, and after a step
# through time it slides to the next day, and the bar below the window has all of its buttons
# in a window of the size that it opens with. Then that the bar stays between the corners of
# a window that's asked to be smaller than it may be, and that the app has all of the bar
# again when it starts with that window and has it as large as it was. Then that the panel
# closes when the window goes to another page, and explains the same again when the window
# comes back. The picture of the first part is left in vision/build/sim/vision.png to look
# at. A simulator that the check started is shut down again.
#
#   vision/check.sh [app] [simulator name]     "Apple Vision Pro" by default, app as for build.sh
set -euo pipefail

cd "$(dirname "$0")/.."
only=()
if [ "${1:-}" = app ]; then
    only=(app)
    shift
fi
device=${1:-Apple Vision Pro}
udid=$(./bld app-simulator vision-sim "$device")
booted=$(xcrun simctl list devices | grep -F "$udid" | grep -c Booted || true)
folder=$(mktemp -d)
trap 'rm -rf "$folder"; [ "$booted" = 1 ] || xcrun simctl shutdown "$udid" > /dev/null 2>&1 || true' EXIT
vision/build.sh ${only[@]+"${only[@]}"} "$device" > "$folder/built" 2>&1 || { cat "$folder/built" >&2; exit 1; }

# What the page says of itself. Then the chart of someone born in 1975, through a link that
# shares it, a step to the next day there, a part that's chosen on the page, another step, the
# chart of someone born in 2001, and a page of transits by month: the script runs in each
# page that loads, and the page keeps count of them. The Sun is tapped in each chart that's built.
script="const shown = id => getComputedStyle(document.getElementById(id)).display !== 'none';
const said = [document.documentElement.className, shown('menu-toggle'), shown('change-time'), shown('explore-card'), shown('svg-image'), shown('page-heading'), getComputedStyle(document.getElementById('title-bar')).position].join(' ');
const step = Number(sessionStorage.getItem('probe') || 0);
sessionStorage.setItem('probe', String(step + 1));
const born = (when, after) => setTimeout(() => { location.href = '/wheel/birth#share&datetime=' + when + '&location=2643743&city=London'; }, after);
const later = () => document.getElementById('day-plus').click();
if (step === 0) { born('1975-06-21T14%3A30', 500); }
if (step === 1) { later(); setTimeout(() => showExploreCard(exploreItems('center.SACRAL')), 1500); setTimeout(later, 3000); born('2001-02-03T04%3A05', 6000); }
if (step === 2) { setTimeout(() => { location.href = '/range'; }, 2500); }
return said"
SIMCTL_CHILD_GATES_VOLUME=planet.1.SUN SIMCTL_CHILD_GATES_EVAL="$script" SIMCTL_CHILD_GATES_SIZE=1000x1120 \
    perl -e 'alarm 90; exec @ARGV' xcrun simctl launch --console-pty --terminate-running-process "$udid" com.uwyn.gatesofhumanity > "$folder/told" 2>&1 &
for _ in $(seq 70); do
    [ "$(grep -ac "^probe " "$folder/told" || true)" -ge 4 ] && break
    python3 -c "import time; time.sleep(1)"
done
python3 -c "import time; time.sleep(1)"
xcrun simctl io "$udid" screenshot vision/build/sim/vision.png > /dev/null 2>&1 || true
xcrun simctl terminate "$udid" com.uwyn.gatesofhumanity > /dev/null 2>&1 || true

told() {
    grep -a "^$1" "$folder/told" | tr -d '\r' || true
}
page=$(told "probe " | head -1)
if [[ "$page" != "probe success(in-app on-vision false false false true true static)"* ]]; then
    echo "A page isn't one of the app for visionOS, it says: ${page:-nothing}" >&2
    exit 1
fi
shapes=$(told "volume [0-9]" | sed -n 1p | sed -E 's/volume ([0-9]+) .*/\1/')
if [ "${shapes:-0}" -lt 400 ]; then
    echo "The chart in the room wasn't built out of what the site describes, the app says: $(told "volume " | head -1)" >&2
    exit 1
fi
# The chart in the room is the one that the page shows, from one page and one day to the
# next. A page that shows no chart, as the transits by month, leaves it there.
charts=$(told "volume [0-9]" | sed -E 's|.* shapes of /wheel/volume/||; s|\?.*||' | sed 1d | tr '\n' ' ')
if [ "$charts" != "birth/197506211430/2643743 birth/197506221430/2643743 birth/197506231430/2643743 birth/200102030405/2643743 " ] ||
   told "volume " | sed -n '/^volume [0-9]/,$p' | grep -q "^volume of no chart"; then
    echo "The chart in the room didn't follow the page to someone born in 1975, through two days, to someone born in 2001, and stay there: $(told "volume " | sed -E 's|.* shapes of /wheel/volume/||' | tr '\n' ' ')" >&2
    exit 1
fi
explained=$(told "explained " | head -1)
if [[ "$explained" != "explained Personality Sun "* ]]; then
    echo "A tap on the Sun in the room didn't have the panel explain it, the panel says: ${explained:-nothing}" >&2
    exit 1
fi
# The lines from the planets to their gates aren't there until the one of what's tapped is,
# and no other with it. It stays through a step to the next day, and goes when the page
# explains something else, or nothing anymore: in each of the four charts it comes with the
# tap, and it goes with the first chart, with the part that's chosen on the page between the
# two steps, with the chart of 1975 and with the one of 2001.
lines=$(told "quiet " | sed -E 's/quiet ([0-9]+) there/\1/' | uniq | tr '\n' ' ')
if [ "$lines" != "0 1 0 1 0 1 0 1 0 " ]; then
    echo "The line of the Sun doesn't come with a tap on it, stay through a day and go with what the page explains: ${lines:-nothing}" >&2
    exit 1
fi
# In the chart of someone else the planets are elsewhere, and there at once. A day later
# each has a little further to go, and slides there with its stem and its sign, while a
# number that steps onto the box of its gate is there at once.
moved=$(told "moved ")
slid() {
    printf '%s\n' "$moved" | sed -n "$1p" | sed -E 's/^moved ([0-9]+) sliding, ([0-9]+) at once$/\1/'
}
other() {
    printf '%s\n' "$moved" | sed -n "$1p" | sed -E 's/^moved ([0-9]+) sliding, ([0-9]+) at once$/\2/'
}
if [ "$(printf '%s\n' "$moved" | wc -l | tr -d ' ')" != 4 ] || [ "$(other 1)" -lt 30 ] || [ "$(slid 2)" -lt 30 ] || [ "$(other 2)" -gt 12 ] ||
   [ "$(slid 3)" -lt 30 ] || [ "$(other 3)" -gt 12 ] || [ "$(other 4)" -lt 30 ]; then
    echo "The chart in the room isn't there at once for someone else, or doesn't slide to the next day: $(printf '%s' "$moved" | tr '\n' ';')" >&2
    exit 1
fi
# The bar below the window leaves the bottom corners of the window free, which are what the
# window is sized by. In a window of the size that it opens with, the bar of a chart has all
# of its buttons.
wider() {
    told "below " | sed -E 's/^below ([0-9]+) of ([0-9]+)$/\1 \2/' | awk "$1"
}
if [ -z "$(wider '$2 == 912 && $1 >= 800 && $1 <= $2')" ] || [ -z "$(wider 'END { if ($1 <= $2) print }')" ]; then
    echo "The bar below a window of 1000 wide doesn't have all of its buttons between the corners of the window: $(told "below " | tr '\n' ';')" >&2
    exit 1
fi

# A window that's asked to be smaller than the smallest that it may be, which is as wide as
# the narrowest bar below it and its corners. The app is ended with what it's launched by,
# not by the simulator: the next one starts with its window then, where a bar with all of its
# buttons was never shown.
SIMCTL_CHILD_GATES_SIZE=300x300 \
    perl -e 'alarm 40; exec @ARGV' xcrun simctl launch --console-pty --terminate-running-process "$udid" com.uwyn.gatesofhumanity > "$folder/told" 2>&1 &
small=$!
for _ in $(seq 20); do
    grep -aq "^below [0-9]* of 372" "$folder/told" && break
    python3 -c "import time; time.sleep(1)"
done
python3 -c "import time; time.sleep(2)"
kill -ALRM "$small" 2> /dev/null || true
wait "$small" 2> /dev/null || true
if [ -z "$(wider 'END { if ($2 == 372 && $1 <= $2) print }')" ]; then
    echo "The bar below the smallest window isn't between the corners of a window of 460 wide: $(told "below " | tr '\n' ';')" >&2
    exit 1
fi

# The window goes from a chart with a part explained to Learn and back: the script runs in
# each page that loads, and the page keeps count of them. The app starts with the smallest
# window, which it has from the one before, and has it as large as it opens.
script="const step = Number(sessionStorage.getItem('probe') || 0);
sessionStorage.setItem('probe', String(step + 1));
if (step === 0) { appChose('center.SACRAL'); setTimeout(() => { location.href = '/learn'; }, 2500); return 'chart'; }
if (step === 1) { setTimeout(() => history.back(), 2500); return 'learn'; }
return 'back';"
SIMCTL_CHILD_GATES_EVAL="$script" SIMCTL_CHILD_GATES_SIZE=1000x1120 \
    perl -e 'alarm 60; exec @ARGV' xcrun simctl launch --console-pty --terminate-running-process "$udid" com.uwyn.gatesofhumanity > "$folder/told" 2>&1 &
for _ in $(seq 40); do
    grep -aq "^probe success(back)" "$folder/told" && break
    python3 -c "import time; time.sleep(1)"
done
xcrun simctl terminate "$udid" com.uwyn.gatesofhumanity > /dev/null 2>&1 || true
went=$(told "explained \|probe success" | sed -E 's/^explained (Sacral|nothing).*/\1/; s/^probe success\(([a-z]+)\).*/\1/' | tr '\n' ' ')
if [ "$went" != "Sacral chart nothing learn Sacral back " ]; then
    echo "The panel doesn't close with the page that it explained and come back with it: ${went:-nothing}" >&2
    exit 1
fi
# the bar as it is when the first page says what it is, before another page has it made again
grown=$(tr -d '\r' < "$folder/told" | awk '/^below / { small = small || $4 == 372; bar = $2; room = $4 } /^probe / { if (small && room == 912 && bar >= 800 && bar <= room) print bar; exit }')
if [ -z "$grown" ]; then
    echo "The bar below a window that the app started small with doesn't have all of its buttons in a window of 1000 wide: $(told "below \|probe success" | cut -c1-30 | tr '\n' ';')" >&2
    exit 1
fi
echo "The app for visionOS has its pages under their name, without their menu and step buttons, builds the chart in the room out of $shapes shapes, has it slide to the next day and show another chart at once, explains what's tapped there in its panel with the one line that belongs to it, closes the panel while the window is at another page, and keeps the bar below the window between its corners"
