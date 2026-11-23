#!/usr/bin/env bash
# Checks that the people of the pages are kept the same with those of another device, from the
# pages to the app and back: who's only here goes there, who's only there comes here, who's
# another on each side is the one of there when that's not who both had, and who's removed here
# is removed there. A simulator has no account of iCloud, so the app takes the people of
# GATES_ICLOUD for it, and tells who that stand-in has at the end. How the two sides are made
# the same is checked by the tests of the site, in MainTest. The app is the one that
# ios/build.sh built and installed in the simulator.
#
#   ios/check-sync.sh [simulator name]     "iPhone 17" by default, which it leaves booted
set -euo pipefail

device=${1:-iPhone 17}
cd "$(dirname "$0")/.."

udid=$(./bld app-simulator ios-sim "$device")
xcrun simctl bootstatus "$udid" -b > /dev/null

# runs a script in the Birth Chart, beside an iCloud with some people, and prints what the app tells
run() {
    SIMCTL_CHILD_GATES_START=/wheel/birth SIMCTL_CHILD_GATES_ICLOUD="$1" SIMCTL_CHILD_GATES_EVAL="$2" perl -e 'alarm 90; exec @ARGV' \
        xcrun simctl launch --console-pty --terminate-running-process "$udid" com.uwyn.gatesofhumanity 2>&1 |
        awk '/^probe / { print } /^elsewhere / { print; exit }' | tr -d '\r'
}
person() {
    printf '{"datetime": "%s-01-02T03:04", "locationId": "2800866", "locationCity": "Brussels, Brussels Capital, Belgium"%s}' "$1" "${2:-}"
}

# The people of this device, saved while iCloud has nobody. Who the simulator had before is
# removed one at a time, as the pages do it: all of them gone at once is what a device lost,
# which the app would get back.
saved="const wait = ms => new Promise(resolve => setTimeout(resolve, ms));
for (const name of Object.keys(HdStore.load(browserStorage()).charts)) {
    HdStore.update(browserStorage(), data => HdStore.removeChart(data, name));
    await wait(150);
}
HdStore.update(browserStorage(), data => {
    HdStore.putChart(data, 'Here', $(person 1980));
    HdStore.putChart(data, 'Both', $(person 1975));
});
return 'saved';"
run '{}' "$saved" > /dev/null

# then beside an iCloud with people of another device
told=$(run "{\"There\": $(person 1990 ', "group": "Family"'), \"Both\": $(person 1976)}" "
const wait = ms => new Promise(resolve => setTimeout(resolve, ms));
const people = () => Object.entries(HdStore.load(browserStorage()).charts).map(([name, chart]) => name + ' ' + chart.datetime.slice(0, 4) + ' ' + (chart.group || '')).sort().join(', ');
for (let waits = 0; waits < 50 && !people().includes('There'); waits++) await wait(200);
const first = people();
HdStore.update(browserStorage(), data => HdStore.removeChart(data, 'Here'));
await wait(1500);
return 'people ' + first + ' then ' + people();")

here=$(printf '%s\n' "$told" | sed -nE 's/^probe .*people (.*) then (.*)\), [0-9]+ requests.*/\1 | \2/p')
there=$(printf '%s\n' "$told" | sed -n 's/^elsewhere //p')
expected_here="Both 1976 , Here 1980 , There 1990 Family | Both 1976 , There 1990 Family"
expected_there='[["Both","1976-01-02T03:04",""],["There","1990-01-02T03:04","Family"]]'
if [ "$here" != "$expected_here" ] || [ "$there" != "$expected_there" ]; then
    echo "The people aren't kept the same. The pages had, and then after one was removed:" >&2
    echo "  $here" >&2
    echo "  where it should be $expected_here" >&2
    echo "and iCloud would have $there" >&2
    echo "  where it should be $expected_there" >&2
    exit 1
fi
echo "The pages got who was saved elsewhere, iCloud who was saved here, and who was removed here is gone there"
