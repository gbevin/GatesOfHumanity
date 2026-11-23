#!/usr/bin/env bash
# Checks with a finger that a page of the app scrolls as a screen of an app does: pulled past its
# top or its bottom it gives and comes back, under a title bar that stays where it is, a flick
# glides on as far as iOS has it, a page that fits on the screen gives too, and a tap on the
# status bar or on the title of the page takes a page that's scrolled down back to its top. Nothing can put a finger on a
# simulator from a command line but a UI test, so feel/Feel.swift does that, while a script in
# the page keeps where the page was each frame. The app is the one that
# ios/build.sh built and installed in the simulator.
#
#   ios/check-feel.sh [simulator name]     "iPhone 17" by default, which it leaves booted
set -euo pipefail

device=${1:-iPhone 17}
cd "$(dirname "$0")/.."

udid=$(./bld app-simulator ios-sim "$device")
xcrun simctl bootstatus "$udid" -b > /dev/null

# a pull down at the top, a pull up at the bottom, a flick from the top, and a pull down on a page that fits
drags="0.45 0.75 200 1.0,0.6 0.3 200 1.0,0.75 0.35 1500 0,0.45 0.75 200 1.0"
script="const body = document.body, bar = document.getElementById('title-bar');
const wait = ms => new Promise(resolve => setTimeout(resolve, ms));
const told = [];
let hid = [];
// what the UI test waits for before it touches anything
const ready = document.createElement('button');
ready.setAttribute('aria-label', 'Ready for a finger');
ready.style.cssText = 'position: fixed; top: 0; left: 0; width: 1px; height: 1px; padding: 0; border: 0; opacity: 0.01';
body.appendChild(ready);
for (let touch = 0; touch < 4; touch++) {
    body.scrollTop = touch === 1 ? 1e6 : 0;
    if (touch === 3) {
        hid = [...document.querySelectorAll('#content > :not(#controls)')].filter(part => !part.hidden);
        for (const part of hid) part.hidden = true;
    }
    await wait(400);
    let touching = false, began = 0, ended = 0, barMoved = 0;
    const samples = [];
    const down = () => { touching = true; began = began || performance.now(); };
    const up = () => { touching = false; ended = performance.now(); };
    addEventListener('touchstart', down, true); addEventListener('touchend', up, true); addEventListener('touchcancel', up, true);
    const limit = performance.now() + 150000;
    while (performance.now() < limit && !(ended && performance.now() - ended > 2800)) {
        await new Promise(resolve => requestAnimationFrame(resolve));
        if (began) {
            samples.push([performance.now(), body.scrollTop, touching]);
            barMoved = Math.max(barMoved, Math.abs(bar.getBoundingClientRect().top));
        }
    }
    removeEventListener('touchstart', down, true); removeEventListener('touchend', up, true); removeEventListener('touchcancel', up, true);
    if (!began) break;
    const tops = samples.map(sample => sample[1]), held = samples.filter(sample => sample[2]);
    const release = held[held.length - 1] || samples[0], before = held[held.length - 4] || samples[0];
    told.push({least: Math.min(...tops), most: Math.max(...tops), end: tops[tops.length - 1], release: release[1],
               speed: (release[1] - before[1]) / Math.max(1, release[0] - before[0]) * 1000,
               room: body.scrollHeight - body.clientHeight, bar: barMoved, window: window.scrollY});
}
// then the status bar is tapped over a page that's scrolled down, which the UI test waits for too
for (const part of hid) part.hidden = false;
body.scrollTop = 900;
await wait(400);
const from = body.scrollTop;
ready.setAttribute('aria-label', 'Ready for the status bar');
const tapped = performance.now();
while (performance.now() - tapped < 30000 && body.scrollTop > 0) await wait(100);
const after = body.scrollTop;
// and then the title in the bar of the page, the same way
body.scrollTop = 900;
await wait(400);
ready.setAttribute('aria-label', 'Ready for the title');
const titled = performance.now();
while (performance.now() - titled < 30000 && body.scrollTop > 0) await wait(100);
return 'feel ' + JSON.stringify(told) + ' tap ' + JSON.stringify({from: from, to: after}) + ' title ' + JSON.stringify({to: body.scrollTop});"
told=$(mktemp)
SIMCTL_CHILD_GATES_START=/wheel/birth SIMCTL_CHILD_GATES_EVAL="$script" perl -e 'alarm 240; exec @ARGV' \
    xcrun simctl launch --console-pty --terminate-running-process "$udid" com.uwyn.gatesofhumanity > "$told" 2>&1 &
# the tests of the class: the drags, then the tap on the status bar, then the one on the title
TEST_RUNNER_FEEL_DRAGS="$drags" ios/feel/run.sh "$udid" Feel
# the page tells a few seconds after the last touch, and the app is stopped rather than waited for
for _ in $(seq 1 45); do
    grep -aq "^probe " "$told" && break
    python3 -c "import time; time.sleep(1)"
done
xcrun simctl terminate "$udid" com.uwyn.gatesofhumanity > /dev/null 2>&1 || true
wait
python3 - "$told" <<'PYTHON'
import json, re, sys
found = re.search(r"feel (\[.*\])", open(sys.argv[1], errors="replace").read())
touches = json.loads(found.group(1)) if found else []
if len(touches) != 4:
    sys.exit(f"The page told of {len(touches)} touches where 4 were made")
tap = json.loads(re.search(r" tap (\{.*?\})", found.string).group(1))
title = json.loads(re.search(r" title (\{.*?\})", found.string).group(1))
top, bottom, flick, short = touches
wrong = []
def check(right, what):
    if not right:
        wrong.append(what)
# a finger that moves 260 points takes the page about 117 along, on the curve that iOS has for it
check(top["least"] < -80 and abs(top["end"]) < 1, f"pulled down at its top the page went to {top['least']:.0f} and back to {top['end']:.0f}")
check(bottom["most"] > bottom["room"] + 80 and abs(bottom["end"] - bottom["room"]) < 1,
      f"pulled up at its bottom, at {bottom['room']}, the page went to {bottom['most']:.0f} and back to {bottom['end']:.0f}")
# what iOS slows a scroll by leaves about half a second of the speed it was let go at, a web page once a tenth
glide = (flick["end"] - flick["release"]) / flick["speed"] if flick["speed"] > 0 else 0
check(glide > 0.3, f"let go at {flick['speed']:.0f} points a second the page glided {flick['end'] - flick['release']:.0f} on")
check(short["room"] <= 1 and short["least"] < -80 and abs(short["end"]) < 1,
      f"a page with {short['room']} to scroll went to {short['least']:.0f} and back to {short['end']:.0f} when pulled down")
check(all(touch["bar"] < 0.5 and touch["window"] == 0 for touch in touches), "the title bar or the window moved")
check(tap["from"] > 800 and tap["to"] < 1, f"scrolled down to {tap['from']:.0f}, a tap on the status bar left the page at {tap['to']:.0f}")
check(title["to"] < 1, f"scrolled down again, a tap on the title of the page left it at {title['to']:.0f}")
if wrong:
    sys.exit("The page doesn't scroll as a screen of an app: " + "; ".join(wrong))
print(f"Pulled past its top the page gives {-top['least']:.0f} points and past its bottom {bottom['most'] - bottom['room']:.0f}, "
      f"a flick at {flick['speed']:.0f} points a second glides {flick['end'] - flick['release']:.0f} on, "
      f"a page that fits gives {-short['least']:.0f}, the title bar stays, and a tap on the status bar or on the title goes back to the top")
PYTHON
