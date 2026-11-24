#!/usr/bin/env python3
"""Makes the phone screenshots of Google Play: captures the screens of the app in the emulator that
adb sees, then sets each one under its headline, as ios/Store/screenshots.py does for the App Store
and with its screens, people and headlines.

    android/Store/screenshots.py capture RAW    the screens, as the emulator shows them
    android/Store/screenshots.py frame RAW OUT  each under its headline, 1080 x 1920

`capture` builds the app so that its pages can be looked into, removes what it keeps, fills it with
people who don't exist and shows the status bar of Android's demo mode. It installs the app that's
handed out again at the end. It only runs in an emulator. Google Play takes a side at most twice the
other one, so the frames are 9:16, which it also asks for an app to be featured. The widgets aren't
among them: they're placed on a home screen by hand. Needs Pillow and Node."""
import importlib.util
import json
import os
import subprocess
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.join(HERE, "..", "..")
spec = importlib.util.spec_from_file_location("ios_screenshots", os.path.join(ROOT, "ios", "Store", "screenshots.py"))
ios = importlib.util.module_from_spec(spec)
spec.loader.exec_module(ios)

ADB = os.path.join(os.environ.get("ANDROID_HOME", os.path.expanduser("~/Library/Android/sdk")), "platform-tools", "adb")
PORT = "9232"
SIZE = (1080, 1920)
SHOTS = [shot for shot in ios.SHOTS if shot[2] is not None]


def adb(*arguments):
    return subprocess.run([ADB, *arguments], check=True, capture_output=True, text=True).stdout


def demo(command, **extras):
    told = [part for name, value in extras.items() for part in ("-e", name, value)]
    adb("shell", "am", "broadcast", "-a", "com.android.systemui.demo", "-e", "command", command, *told)


def capture(raw):
    if adb("shell", "getprop", "ro.kernel.qemu").strip() != "1":
        sys.exit("This removes what the app keeps, so it only runs in an emulator, and adb sees none")
    os.makedirs(raw, exist_ok=True)
    subprocess.run([os.path.join(ROOT, "android", "build.sh"), "app"], env=dict(os.environ, GATES_PROBED="1"), check=True, capture_output=True)
    try:
        adb("shell", "pm", "clear", "com.uwyn.gatesofhumanity")
        adb("shell", "settings", "put", "global", "sysui_demo_allowed", "1")
        demo("enter")
        demo("clock", hhmm="0941")
        demo("battery", level="100", plugged="false")
        demo("network", wifi="show", level="4")
        demo("network", mobile="hide")
        demo("notifications", visible="false")
        adb("shell", "am", "start", "-n", "com.uwyn.gatesofhumanity/.MainActivity")
        told = {"stored": json.dumps(ios.stored()), "shots": [[file, start, script] for file, _, start, script in SHOTS]}
        subprocess.run(["node", os.path.join(HERE, "capture.mjs"), ADB, raw], input=json.dumps(told), text=True, check=True,
                       env=dict(os.environ, CDP_PORT=PORT))
    finally:
        demo("exit")
        subprocess.run([ADB, "forward", "--remove", "tcp:" + PORT], capture_output=True)
        # the app that's handed out again, built from the same sources
        subprocess.run([os.path.join(ROOT, "android", "build.sh"), "app"], check=True, capture_output=True)


def main():
    arguments = sys.argv[1:]
    if len(arguments) == 2 and arguments[0] == "capture":
        capture(arguments[1])
    elif len(arguments) == 3 and arguments[0] == "frame":
        for shot in SHOTS:
            ios.frame(shot, arguments[1], arguments[2].rstrip("/"), SIZE)
        print(f"{len(SHOTS)} frames written to {arguments[2]}")
    else:
        sys.exit(__doc__)


if __name__ == "__main__":
    main()
