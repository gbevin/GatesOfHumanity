#!/usr/bin/env bash
# Checks that the app keeps answering when its pages ask for much at once, as a few quick taps on
# a step of a chart do on a phone: 150 charts, descriptions and times asked together have to be
# there within seconds, which they are when a chart reaches the web view in a few pieces and
# not in the thousands of its template. The app is the one that ios/build.sh built and
# installed in the simulator.
#
#   ios/check-burst.sh [simulator name]     "iPhone 17" by default, which it leaves booted
set -euo pipefail

device=${1:-iPhone 17}
# on a Mac, where one of these takes a few milliseconds
within=10000
cd "$(dirname "$0")/.."

udid=$(./bld app-simulator ios-sim "$device")
xcrun simctl bootstatus "$udid" -b > /dev/null

script="const started = Date.now();
const asked = [];
for (let i = 0; i < 150; i++) {
    const day = String(1 + i % 28).padStart(2, '0');
    const month = String(1 + i % 12).padStart(2, '0');
    const paths = ['/wheel/image/birth-transit/198803140725/2800866/2027' + month + day + '0537/2800866',
                   '/wheel/explore/birth-transit/198803140725/2800866/2027' + month + day + '0537/2800866',
                   '/wheel/image/birth/1988' + month + day + '0725/2800866', '/now/2800866'];
    asked.push(Promise.race([fetch(paths[i % paths.length]).then(answer => answer.text()).then(() => Date.now() - started),
                             new Promise(resolve => setTimeout(() => resolve(-1), 90000))]));
}
const times = await Promise.all(asked);
return 'burst ' + times.filter(time => time >= 0).length + ' ' + Math.max(...times);"
# the about page asks for nothing by itself once it's shown, so only the script's requests go on
answered=$(SIMCTL_CHILD_GATES_START=/about SIMCTL_CHILD_GATES_EVAL="$script" perl -e 'alarm 150; exec @ARGV' \
    xcrun simctl launch --console-pty --terminate-running-process "$udid" com.uwyn.gatesofhumanity 2>&1 |
    awk '/^probe / { print; exit }')
read -r count last held <<< "$(printf '%s' "$answered" | tr -d '\r' | sed -nE 's/.*burst ([0-9]+) (-?[0-9]+).*, ([0-9]+) requests held on to.*/\1 \2 \3/p')"
if [ "${count:-0}" != 150 ] || [ "$last" -gt "$within" ]; then
    echo "The app answered ${count:-none} of 150 requests asked at once, the last after ${last:-?} ms, where $within ms is the most" >&2
    exit 1
fi
# an answer that holds on to what answers it is never let go of
if [ "$held" != 0 ]; then
    echo "The app still holds on to $held of the 150 requests that it answered" >&2
    exit 1
fi
echo "The app answered 150 requests asked at once, the last after $last ms, and holds on to none of them"
