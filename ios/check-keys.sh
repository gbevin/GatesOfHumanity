#!/usr/bin/env bash
# Checks that the keys of a keyboard that's connected do what the pages have buttons for, also
# once the page was touched and has the keys first: Shift and Command with the right arrow show
# the chart of a day later, and Command with 3 opens the third of the charts. Nothing can press a key in a simulator from a command line but a UI
# test, so feel/Keys.swift does that, while a script in each page tells where it is and by how
# many days its date changed. The app is the one that ios/build.sh built and installed in the simulator.
#
#   ios/check-keys.sh [simulator name]     "iPhone 17" by default, which it leaves booted
set -euo pipefail

device=${1:-iPhone 17}
cd "$(dirname "$0")/.."

udid=$(./bld app-simulator ios-sim "$device")
xcrun simctl bootstatus "$udid" -b > /dev/null

script="const wait = ms => new Promise(resolve => setTimeout(resolve, ms));
const field = document.getElementById('datetime');
const from = field ? field.value : '';
// what the UI test waits for before it presses anything
const ready = document.createElement('button');
ready.setAttribute('aria-label', 'Ready for keys');
ready.style.cssText = 'position: fixed; top: 0; left: 0; width: 1px; height: 1px; padding: 0; border: 0; opacity: 0.01';
document.body.appendChild(ready);
// the first page waits for the UI test, which takes a while to start, the one after it tells at once
const until = performance.now() + (sessionStorage.getItem('keys') ? 0 : 90000);
sessionStorage.setItem('keys', 'pressed');
while (performance.now() < until && field && field.value === from) await wait(100);
const days = field ? (Date.parse(field.value + 'Z') - Date.parse(from + 'Z')) / 86400000 : 0;
return 'keys ' + location.pathname + ' ' + days + ' end';"
told=$(mktemp)
SIMCTL_CHILD_GATES_START=/wheel/birth SIMCTL_CHILD_GATES_EVAL="$script" perl -e 'alarm 200; exec @ARGV' \
    xcrun simctl launch --console-pty --terminate-running-process "$udid" com.uwyn.gatesofhumanity > "$told" 2>&1 &
ios/feel/run.sh "$udid" Keys
xcrun simctl terminate "$udid" com.uwyn.gatesofhumanity > /dev/null 2>&1 || true
wait
# where each page was, and by how many days its date changed
pages=$(tr -d '\r' < "$told" | sed -nE 's/^probe .*keys ([^ ]+) ([^ ]+) end.*/\1 \2/p' | tr '\n' '|')
expected="/wheel/birth 1|/wheel 0|"
case "$pages" in
"$expected"*) echo "Shift and Command with the right arrow show a day later, and Command with 3 opens the Transit Chart";;
*)
    echo "The keys don't do what the pages have buttons for, the pages told: $pages" >&2
    echo "  where it should start with $expected" >&2
    exit 1;;
esac
