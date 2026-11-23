#!/usr/bin/env bash
# Checks what the app does around its pages, in the emulator or the device that adb sees: that
# it marks them as pages of the app, says its version, keeps
# who was saved for the widgets, draws the widgets, opens what a widget is tapped for, and goes
# on when Android stops what draws the pages. It builds the app so that its pages can be looked
# into, see android/build.sh, and leaves the app that's handed out installed again at the end.
# The pictures of the widgets are left in android/build/checks to look at.
#
# It starts by removing what the app keeps, the saved people among it, and leaves two people
# saved who don't exist. So it only runs in an emulator, never on someone's phone.
#
#   android/check-app.sh
set -euo pipefail

cd "$(dirname "$0")/.."
adb=${ANDROID_HOME:-$HOME/Library/Android/sdk}/platform-tools/adb
if [ "$("$adb" shell getprop ro.kernel.qemu 2> /dev/null | tr -d '\r')" != 1 ]; then
    echo "android/check-app.sh removes what the app keeps, so it only runs in an emulator, and adb sees none" >&2
    exit 1
fi
port=9231
out=android/build/checks
rm -rf "$out"
mkdir -p "$out"

GATES_PROBED=1 android/build.sh app > /dev/null
# with nothing kept, as the first time
"$adb" shell pm clear com.uwyn.gatesofhumanity > /dev/null
"$adb" shell am start -n com.uwyn.gatesofhumanity/.MainActivity > /dev/null
failed=0
CDP_PORT=$port node android/check-app.mjs "$adb" "$out" || failed=1
"$adb" forward --remove tcp:$port > /dev/null 2>&1 || true
# the app that's handed out again, built from the same sources
android/build.sh app > /dev/null ||
    { echo "The app that's handed out couldn't be installed again: the emulator still has the one that can be looked into" >&2; failed=1; }
exit $failed
