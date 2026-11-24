#!/usr/bin/env python3
"""Makes the screenshots of the App Store: captures the screens of the app in a simulator, then
sets each one under a headline, on the night sky of the icon.

    ios/Store/screenshots.py capture "<simulator name>" RAW    the screens, as the simulator shows them
    ios/Store/screenshots.py home "<simulator name> Capture" RAW   the Home Screen with the widgets
    ios/Store/screenshots.py frame RAW OUT                     each under its headline, at RAW's size

`capture` installs the app that `ios/build.sh` built, in a simulator that it boots and shuts down
again unless it was running already, and fills it with people who don't exist. `home` does the
same in a simulator that was made for captures, whose Home Screen it changes for good: a night
for a wallpaper, no names under the icons, and nothing on its first page but the widgets of the
app, which a UI test puts there. `frame` writes a set of iPhone frames a second time into OUT-6.3, at
the size of the 6.3-inch display that App Store Connect requires. Needs Pillow."""
import json
import math
import os
import re
import subprocess
import sys
import threading
import time

from PIL import Image, ImageDraw, ImageFilter, ImageFont

BUNDLE = "com.uwyn.gatesofhumanity"
APP = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "build", "sim", "GatesOfHumanity.app")

# first names alone, with births that are nobody's
PEOPLE = {
    "Nora": ("1988-03-14T07:25", "2800866", "Brussels, Brussels Capital, Belgium", "Family"),
    "Elias": ("1985-10-02T22:40", "2618425", "Copenhagen, Capital Region, Denmark", "Family"),
    "Mila": ("2016-06-21T13:05", "2800866", "Brussels, Brussels Capital, Belgium", "Family"),
    "Tobias": ("1991-01-09T04:50", "3067696", "Prague, Czechia", "Friends"),
    "Ines": ("1979-08-27T16:15", "2267057", "Lisbon, Portugal", "Friends"),
    "Ruben": ("1994-12-03T10:30", "2964574", "Dublin, Leinster, Ireland", "Clients"),
    "Saskia": ("1972-05-18T19:45", "6167865", "Toronto, Ontario, Canada", "Clients"),
}
FIRST, SECOND = "Nora", "Elias"


def stored():
    """What the app keeps: the people, with the first two as the chart that's shown."""
    def chart(name):
        datetime, location, city, group = PEOPLE[name]
        return {"datetime": datetime, "locationId": location, "locationCity": city, "group": group}

    def birth(name):
        datetime, location, city, _ = PEOPLE[name]
        return {"datetime": datetime, "location": {"id": location, "city": city}}

    def named(name):
        datetime, location, _, _ = PEOPLE[name]
        return {"name": name, "datetime": datetime, "locationId": location}

    return {"version": 1, "charts": {name: chart(name) for name in PEOPLE},
            "location": birth(FIRST)["location"], "birth": birth(FIRST), "partner": birth(SECOND),
            "named": named(FIRST), "namedPartner": named(SECOND), "guideOpen": False}


# The frames in the order of the store: the file, the two lines of the headline, the page that the
# app starts at and what's done in it before the capture, as a script that the page runs.
TAP = """
const part = [...document.querySelectorAll('#svg-image svg [data-explore]')].find(each => each.dataset.explore.startsWith('%s'));
const box = part.getBoundingClientRect();
part.dispatchEvent(new MouseEvent('click', {bubbles: true, clientX: box.x + box.width / 2, clientY: box.y + box.height / 2}));
return part.dataset.explore;
"""
ENLARGE = """
const pause = () => new Promise(resolve => setTimeout(resolve, 500));
document.getElementById('chart-zoom').click();
await pause();
document.getElementById('chart-viewer-in').click();
await pause();
return document.getElementById('chart-viewer').hidden ? 'closed' : 'enlarged';
"""
SHOTS = [
    ("1-chart", ["Your Human Design", "birth chart"], "/wheel/birth", "return document.title;"),
    ("2-explore", ["Tap any part", "to read what it is"], "/wheel/birth", TAP % "channel.3-60"),
    ("3-connection", ["See what connects", "two people"], "/wheel/connection", "return document.title;"),
    ("4-transits", ["The transits", "of any moment"], "/wheel/birth-transit", "return document.title;"),
    ("5-month", ["Every change", "day by day"], "/range", "return document.title;"),
    ("6-closer", ["A closer look", "at every detail"], "/wheel/birth", ENLARGE),
    ("7-learn", ["Learn Human Design", "a topic at a time"], "/learn", "return document.title;"),
    ("8-people", ["Your people", "stay in the app"], "/people", "return document.title;"),
    # not a page of the app: its widgets on the Home Screen, see home()
    ("9-widgets", ["Widgets for what's", "happening now"], None, None),
]
# The widgets of the Home Screen, as their places among those that the app offers, counted from zero
# in the order of their sizes. On a phone the large one of a person, the small body graph and the
# small transits: the last one is added at the top, and together they fill a page. A tablet puts
# them in the order they're added and has room for seven, the large ones first.
WIDGETS = {False: "7,2,0", True: "7,4,1,3,6,0,2"}
CAPTURE = " Capture"
# the wallpaper behind them: a night that's deeper than theirs, of the sky that the frames have
NIGHT = "111133"
FEEL = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "feel", "run.sh")

# the colors of the icon, see ios/icon.py
SKY = [(0.0, (59, 59, 138)), (0.5, (31, 31, 82)), (1.0, (11, 11, 36))]
GOLD = [(251, 214, 126), (216, 146, 42)]
RING = (164, 164, 222)
WHITE = (255, 255, 255)
FACE = "/System/Library/Fonts/HelveticaNeue.ttc"
BOLD = 1
# the 6.3-inch display, the same shape as the 6.9-inch one to within a tenth of a percent
LARGE, MEDIUM = (1320, 2868), (1206, 2622)


def run(*command, **options):
    return subprocess.run(command, check=True, capture_output=True, text=True, **options).stdout


def simulator(name):
    for line in run("xcrun", "simctl", "list", "devices", "available").split("\n"):
        match = re.match(r"\s+" + re.escape(name) + r" \(([0-9A-F-]{36})\) \((\w+)\)", line)
        if match:
            return match.group(1), match.group(2) == "Booted"
    sys.exit(f"no simulator is called {name}")


def capture(name, raw):
    if not os.path.isdir(APP):
        sys.exit("ios/build.sh hasn't built the app for the simulator yet")
    os.makedirs(raw, exist_ok=True)
    udid, booted = simulator(name)
    if not booted:
        subprocess.run(["xcrun", "simctl", "boot", udid], check=True)
    try:
        run("xcrun", "simctl", "bootstatus", udid, "-b")
        run("xcrun", "simctl", "install", udid, APP)
        run("xcrun", "simctl", "status_bar", udid, "override", "--time", "9:41", "--batteryState", "discharging",
            "--batteryLevel", "100", "--wifiBars", "3", "--cellularBars", "4", "--operatorName", "")
        fill = "localStorage.setItem('hdapp', " + json.dumps(json.dumps(stored())) + "); return 'filled';"
        shown(udid, "/about", fill)
        for file, _, start, script in SHOTS:
            if start is None:
                continue
            said = shown(udid, start, script)
            # what the script started, a list that opens or a page that scrolls, has to come to rest
            time.sleep(2)
            run("xcrun", "simctl", "io", udid, "screenshot", os.path.join(raw, file + ".png"))
            print(f"{name}: {file} ({said})")
        subprocess.run(["xcrun", "simctl", "terminate", udid, BUNDLE], capture_output=True)
        run("xcrun", "simctl", "status_bar", udid, "clear")
    finally:
        if not booted:
            subprocess.run(["xcrun", "simctl", "shutdown", udid], check=True)


def touched(udid, test, **told):
    """Runs a UI test, which does to the Home Screen what a finger would."""
    done = subprocess.run([FEEL, udid, "HomeScreen/" + test], env=dict(os.environ, **{"TEST_RUNNER_" + name: value for name, value in told.items()}))
    if done.returncode != 0:
        sys.exit(f"the UI test {test} failed")


def home(name, udid, file):
    """The Home Screen with the widgets of the app, which a UI test puts there as a finger would and
    takes off again, in a simulator that was made for captures: its Home Screen is changed for
    good. It gets a night for a wallpaper and its icons without their names, and its first page
    loses what iOS put there, widgets with the news of the day among it, so that nothing is
    beside the widgets of the app. The widget of a person shows the one whose chart the app
    showed last."""
    if not name.endswith(CAPTURE):
        sys.exit(f"{name} isn't a simulator made for captures, whose name ends with{CAPTURE}: its Home Screen would be changed for good")
    touched(udid, "testWallpaper", FEEL_COLOR=NIGHT)
    touched(udid, "testWithoutNames")
    touched(udid, "testRemoveWidgets", FEEL_ALL="1")
    touched(udid, "testAddWidgets", FEEL_WIDGETS=WIDGETS[name.startswith("iPad")])
    # iOS asks the app's extension for each of them
    time.sleep(10)
    run("xcrun", "simctl", "io", udid, "screenshot", file)
    touched(udid, "testRemoveWidgets")


def capture_home(name, raw):
    if not os.path.isdir(APP):
        sys.exit("ios/build.sh hasn't built the app for the simulator yet")
    os.makedirs(raw, exist_ok=True)
    udid, booted = simulator(name)
    if not booted:
        subprocess.run(["xcrun", "simctl", "boot", udid], check=True)
    try:
        run("xcrun", "simctl", "bootstatus", udid, "-b")
        run("xcrun", "simctl", "install", udid, APP)
        run("xcrun", "simctl", "status_bar", udid, "override", "--time", "9:41", "--batteryState", "discharging",
            "--batteryLevel", "100", "--wifiBars", "3", "--cellularBars", "4", "--operatorName", "")
        fill = "localStorage.setItem('hdapp', " + json.dumps(json.dumps(stored())) + "); return 'filled';"
        shown(udid, "/about", fill)
        # the app tells its widgets who was saved when a page has them
        shown(udid, "/wheel/birth", "return document.title;")
        subprocess.run(["xcrun", "simctl", "terminate", udid, BUNDLE], capture_output=True)
        home(name, udid, os.path.join(raw, "9-widgets.png"))
        print(f"{name}: 9-widgets")
        run("xcrun", "simctl", "status_bar", udid, "clear")
    finally:
        if not booted:
            subprocess.run(["xcrun", "simctl", "shutdown", udid], check=True)


def shown(udid, start, script, **probes):
    """Starts the app at a page and waits for what its script returned there."""
    environment = dict(os.environ, SIMCTL_CHILD_GATES_START=start, SIMCTL_CHILD_GATES_EVAL=script,
                       **{"SIMCTL_CHILD_" + name: value for name, value in probes.items()})
    launched = subprocess.Popen(["xcrun", "simctl", "launch", "--console-pty", "--terminate-running-process", udid, BUNDLE],
                                env=environment, stdout=subprocess.PIPE, stderr=subprocess.STDOUT, text=True)
    # an app that says nothing would be waited for without end
    patience = threading.Timer(90, launched.kill)
    patience.start()
    try:
        for line in launched.stdout:
            if line.startswith("probe "):
                if "failure" in line:
                    sys.exit(f"{start}: {line.strip()}")
                return line.strip()
        sys.exit(f"{start}: the app never said what its script returned")
    finally:
        patience.cancel()
        # the app goes on, only the console is let go of
        launched.kill()


# The app for visionOS in a simulator that was made for captures, in the room of the simulator
# that takes the least attention away from the window and is looked at straight on, so that the
# window is in the middle of what's around it: the living room in the evening. The frames in the
# order of the store: the file, the headline, the page, what's done in it, whether the chart is
# in the room beside the window, with the name of a shape that's tapped there, and where the
# picture looks: the middle of what it shows, as a share of the width of what the simulator
# shows, and how much closer it is. Nothing moves the eye of a simulator from a command line,
# so a picture with something beside the window is taken out of a wider one, around the middle
# of the window and what's beside it.
ROOM = "LivingRoomNight"
VISION_APP = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "..", "vision", "build", "sim", "Gates.app")
VISION_SHOTS = [
    ("1-chart", ["Your Human Design", "birth chart"], "/wheel/birth", "return document.title;", None, (0.5, 1.0)),
    ("2-room", ["The chart", "in your room"], "/wheel/birth", "return document.title;", "nothing", (0.39, 1.12)),
    ("3-explore", ["Tap any part", "to read what it is"], "/wheel/birth", TAP % "channel.3-60", None, (0.575, 1.12)),
    ("4-connection", ["See what connects", "two people"], "/wheel/connection", "return document.title;", None, (0.5, 1.0)),
    ("5-transits", ["The transits", "of any moment"], "/wheel/birth-transit", "return document.title;", None, (0.5, 1.0)),
    ("6-month", ["Every change", "day by day"], "/range", "return document.title;", None, (0.5, 1.0)),
    ("7-learn", ["Learn Human Design", "a topic at a time"], "/learn", "return document.title;", None, (0.5, 1.0)),
]


def roomed(udid):
    """Has a simulator of visionOS that just started be in the room of the captures."""
    run("xcrun", "simctl", "bootstatus", udid, "-b")
    # the simulator only looks at its room when it starts
    if run("xcrun", "simctl", "spawn", udid, "defaults", "read", "com.apple.RealityEnvironment", "activeSyntheticEnvironment").strip() != ROOM:
        run("xcrun", "simctl", "spawn", udid, "defaults", "write", "com.apple.RealityEnvironment", "activeSyntheticEnvironment", ROOM)
        run("xcrun", "simctl", "shutdown", udid)
        run("xcrun", "simctl", "boot", udid)
        run("xcrun", "simctl", "bootstatus", udid, "-b")


def capture_vision(name, out):
    if not name.endswith(CAPTURE):
        sys.exit(f"{name} isn't a simulator made for captures, whose name ends with{CAPTURE}: its room would be changed for good")
    if not os.path.isdir(VISION_APP):
        sys.exit("vision/build.sh hasn't built the app for the simulator yet")
    os.makedirs(out, exist_ok=True)
    udid, booted = simulator(name)
    if not booted:
        subprocess.run(["xcrun", "simctl", "boot", udid], check=True)
    try:
        roomed(udid)
        run("xcrun", "simctl", "install", udid, VISION_APP)
        fill = "localStorage.setItem('hdapp', " + json.dumps(json.dumps(stored())) + "); return 'filled';"
        shown(udid, "/about", fill, GATES_SIZE="1000x1120")
        for file, headline, start, script, room, looks in VISION_SHOTS:
            said = shown(udid, start, script, GATES_SIZE="1000x1120", **({"GATES_VOLUME": room} if room else {}))
            # the panel slides out and the chart in the room is built
            time.sleep(8 if room else 4)
            raw = os.path.join(out, file + ".raw.png")
            run("xcrun", "simctl", "io", udid, "screenshot", raw)
            worded(raw, headline, os.path.join(out, file + ".png"), looks)
            os.remove(raw)
            print(f"{name}: {file} ({said})")
        subprocess.run(["xcrun", "simctl", "terminate", udid, BUNDLE], capture_output=True)
    finally:
        if not booted:
            subprocess.run(["xcrun", "simctl", "shutdown", udid], check=True)


# The watch app in the simulator of the watch that's paired with an iPhone's, with the drawings
# that the iPhone app makes for the people here. The frames in the order of the store: the file,
# and the screen, the tap on it and the row that the watch app begins past, see ios/watch.
WATCH = BUNDLE + ".watchkitapp"
WATCH_APP = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "build", "sim", "Gates.app")
WATCH_SHOTS = [
    ("1-transits", {"GATES_SCREEN": "0"}),
    ("2-bodygraph", {"GATES_SCREEN": "1"}),
    ("3-person", {"GATES_SCREEN": "2"}),
    ("4-rows", {"GATES_SCREEN": "0", "GATES_DETAIL": "0"}),
    ("5-explained", {"GATES_SCREEN": "0", "GATES_DETAIL": "0", "GATES_ROW": "1"}),
]


def capture_watch(name, out):
    if not os.path.isdir(APP) or not os.path.isdir(WATCH_APP):
        sys.exit("ios/build.sh hasn't built the apps for the simulators yet")
    os.makedirs(out, exist_ok=True)
    out = os.path.abspath(out)
    phone, phone_booted = simulator(name)
    pairs = json.loads(run("xcrun", "simctl", "list", "pairs", "-j"))["pairs"].values()
    watch = next((pair["watch"]["udid"] for pair in pairs if pair["phone"]["udid"] == phone and "active" in pair["state"]), None)
    if watch is None:
        sys.exit(f"the simulator {name} has no watch")
    watch_booted = "Booted" in next(line for line in run("xcrun", "simctl", "list", "devices").split("\n") if watch in line)
    for udid, booted in ((phone, phone_booted), (watch, watch_booted)):
        if not booted:
            subprocess.run(["xcrun", "simctl", "boot", udid], check=True)
    try:
        for udid in (phone, watch):
            run("xcrun", "simctl", "bootstatus", udid, "-b")
        run("xcrun", "simctl", "install", phone, APP)
        run("xcrun", "simctl", "install", watch, WATCH_APP)
        fill = "localStorage.setItem('hdapp', " + json.dumps(json.dumps(stored())) + "); return 'filled';"
        shown(phone, "/about", fill)
        # the app keeps who was saved when a page has them, and makes the drawings of those people
        shown(phone, "/wheel/birth", "return document.title;")
        time.sleep(3)
        drawings = os.path.join(out, "drawings")
        if os.path.exists(drawings):
            os.remove(drawings)
        subprocess.run(["xcrun", "simctl", "launch", "--terminate-running-process", phone, BUNDLE],
                       env=dict(os.environ, SIMCTL_CHILD_GATES_WATCH=drawings), check=True, capture_output=True)
        for _ in range(60):
            if os.path.exists(drawings):
                break
            time.sleep(1)
        else:
            sys.exit("the iPhone app made nothing for the watch")
        time.sleep(1)
        for file, probes in WATCH_SHOTS:
            subprocess.run(["xcrun", "simctl", "launch", "--terminate-running-process", watch, WATCH],
                           env=dict(os.environ, SIMCTL_CHILD_GATES_DRAWINGS=drawings, **{"SIMCTL_CHILD_" + key: value for key, value in probes.items()}),
                           check=True, capture_output=True)
            time.sleep(5)
            run("xcrun", "simctl", "io", watch, "screenshot", os.path.join(out, file + ".png"))
            print(f"the watch of {name}: {file}")
        os.remove(drawings)
        for udid, app in ((watch, WATCH), (phone, BUNDLE)):
            subprocess.run(["xcrun", "simctl", "terminate", udid, app], capture_output=True)
    finally:
        for udid, booted in ((watch, watch_booted), (phone, phone_booted)):
            if not booted:
                subprocess.run(["xcrun", "simctl", "shutdown", udid], check=True)


# The Mac app, built to be looked into (GATES_PROBED=1 mac/build.sh), which is another app to
# the Mac than the one that someone uses and keeps its own people. It writes a picture of what
# its window shows, and a frame sets that in a window beside its headline.
MAC_APP = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "..", "mac", "build", "Gates.app", "Contents", "MacOS", "Gates")
MAC_WINDOW = "900x1150"
# without the windows of its last run: after one that ended badly, a Mac asks whether to open them again and waits for an answer
FRESH = ["-ApplePersistenceIgnoreState", "YES"]
MAC_FRAME = (2880, 1800)
# the bar of the window, in points: its height, and its three buttons
MAC_BAR, MAC_BUTTONS = 32, ((255, 95, 87), (254, 188, 46), (40, 200, 64))
EDGE = (33, 33, 78)
QUIET = "const quiet = document.createElement('style'); quiet.textContent = 'body::-webkit-scrollbar { display: none; }'; document.head.appendChild(quiet);"


def mac_shown(start, script, file=None):
    """Starts the Mac app at a page, and has it write a picture of what its window shows once
    what its script started came to rest."""
    script = f"{QUIET} const said = await (async () => {{ {script} }})(); await new Promise(resolve => setTimeout(resolve, 2500)); return said;"
    environment = dict(os.environ, GATES_START=start, GATES_EVAL=script, GATES_SIZE=MAC_WINDOW, **({"GATES_SHOT": "1"} if file else {}))
    said = subprocess.run([MAC_APP, *FRESH], env=environment, capture_output=True, text=True, timeout=120).stdout
    probe = next((line for line in said.split("\n") if line.startswith("probe ")), None)
    if probe is None or "failure" in probe:
        sys.exit(f"{start}: {probe or 'the app never said what its script returned'}")
    if file:
        shot = next((line[5:] for line in said.split("\n") if line.startswith("shot ")), None)
        if shot is None:
            sys.exit(f"{start}: the app wrote no picture")
        os.replace(shot, file)
    return probe


def capture_mac(raw):
    if not os.path.exists(MAC_APP) or b".probed" not in subprocess.run(["codesign", "-d", "-v", MAC_APP], capture_output=True).stderr:
        sys.exit("the Mac app isn't one that's built to be looked into: GATES_PROBED=1 mac/build.sh app")
    os.makedirs(raw, exist_ok=True)
    mac_shown("/about", "localStorage.setItem('hdapp', " + json.dumps(json.dumps(stored())) + "); return 'filled';")
    for file, _, start, script in SHOTS:
        if start is not None:
            print(f"Mac: {file} ({mac_shown(start, script, os.path.join(raw, file + '.png'))})")


def frame_mac(shot, raw, out):
    file, headline, start, _ = shot
    if start is None:
        return
    shown = Image.open(os.path.join(raw, file + ".png")).convert("RGB")
    width, height = MAC_FRAME
    canvas = sky(MAC_FRAME).convert("RGBA")
    # the window as a Mac draws it: its bar in the color of the pages, with its buttons, and round corners
    scale = shown.width / int(MAC_WINDOW.split("x")[0])
    bar = round(MAC_BAR * scale)
    window = Image.new("RGB", (shown.width, shown.height + bar), EDGE)
    window.paste(shown, (0, bar))
    draw = ImageDraw.Draw(window)
    for index, color in enumerate(MAC_BUTTONS):
        x, y, r = (20 + index * 20) * scale, bar / 2, 6 * scale
        draw.ellipse((x - r, y - r, x + r, y + r), fill=color)
    tall = round(height * 0.86)
    window = window.resize((round(window.width * tall / window.height), tall), Image.LANCZOS)
    radius = round(10 * scale * tall / (shown.height + bar))
    shape = Image.new("L", window.size, 0)
    ImageDraw.Draw(shape).rounded_rectangle((0, 0, window.width - 1, window.height - 1), radius, fill=255)
    left, down = width - window.width - round(width * 0.07), (height - tall) // 2
    cast = Image.new("L", MAC_FRAME, 0)
    cast.paste(shape, (left, down + round(height * 0.02)))
    night = Image.new("RGBA", MAC_FRAME, (4, 4, 22, 0))
    night.putalpha(cast.filter(ImageFilter.GaussianBlur(height * 0.03)).point(lambda value: round(value * 0.75)))
    canvas.alpha_composite(night)
    canvas.paste(window, (left, down), shape)
    ImageDraw.Draw(canvas).rounded_rectangle((left - 2, down - 2, left + window.width + 1, down + window.height + 1), radius + 2, outline=RING + (255,), width=2)

    # the headline in the room beside the window, its lines one under the other from the same edge
    room = left - round(width * 0.07)
    # every frame has its words at the size at which the widest of them all fits
    letter = round(height * 0.068)
    widest = max(lettering(text.upper(), letter, letter * 0.06).width for each in SHOTS if each[2] is not None for text in each[1])
    letter = min(letter, round(letter * room * 0.9 / widest))
    lines = [lettering(text.upper(), letter, letter * 0.06) for text in headline]
    widest = max(line.width for line in lines)
    x, y = round(width * 0.07) + (room - round(width * 0.07) - widest) // 2, (height - round(letter * 1.24) * len(lines)) // 2
    for index, line in enumerate(lines):
        shadow = Image.new("RGBA", line.size, (6, 6, 26, 0))
        shadow.putalpha(line.filter(ImageFilter.GaussianBlur(letter * 0.16)).point(lambda value: round(value * 0.7)))
        canvas.alpha_composite(shadow, (x, y + round(letter * 0.06)))
        canvas.paste(gold(line) if index == len(lines) - 1 else Image.new("RGB", line.size, WHITE), (x, y), line)
        y += round(letter * 1.24)
    os.makedirs(out, exist_ok=True)
    canvas.convert("RGB").save(os.path.join(out, file + ".png"))


def worded(raw, headline, file, looks=(0.5, 1.0)):
    """A picture of the room with the headline over the top of it, above the window."""
    canvas = Image.open(raw).convert("RGBA")
    width, height = canvas.size
    middle, closer = looks
    if closer != 1.0:
        # down to the bar below the window, which is what a closer look must keep
        wide, tall = round(width / closer), round(height / closer)
        left = min(max(round(width * middle - wide / 2), 0), width - wide)
        top = min(round(height * 0.925) - tall, height - tall)
        canvas = canvas.crop((left, top, left + wide, top + tall)).resize((width, height), Image.LANCZOS)
    letter = round(height * 0.044)
    y = round(height * 0.03)
    for index, text in enumerate(headline):
        line = lettering(text.upper(), letter, letter * 0.06)
        shadow = Image.new("RGBA", line.size, (6, 6, 26, 0))
        shadow.putalpha(line.filter(ImageFilter.GaussianBlur(letter * 0.16)).point(lambda value: round(value * 0.8)))
        at = ((width - line.width) // 2, y)
        canvas.alpha_composite(shadow, (at[0], at[1] + round(letter * 0.06)))
        color = gold(line) if index == len(headline) - 1 else Image.new("RGB", line.size, WHITE)
        canvas.paste(color, at, line)
        y += round(letter * 1.24)
    canvas.convert("RGB").save(file)


def sky(size):
    """The ground of the icon: lighter around a point above the middle, night at the edges."""
    width, height = size
    small = Image.new("RGB", (width // 8, height // 8))
    pixels = small.load()
    center = (small.width / 2, small.height * 0.2)
    reach = math.hypot(small.width, small.height) * 0.62
    for y in range(small.height):
        for x in range(small.width):
            at = min(1.0, math.hypot(x - center[0], y - center[1]) / reach)
            for (low, a), (high, b) in zip(SKY, SKY[1:]):
                if at <= high:
                    share = (at - low) / (high - low)
                    pixels[x, y] = tuple(round(a[i] + (b[i] - a[i]) * share) for i in range(3))
                    break
    return small.resize(size, Image.BICUBIC)


def lettering(text, size, tracking):
    """A line in capitals with room between its letters, as the titles of the pages are set."""
    font = ImageFont.truetype(FACE, size, index=BOLD)
    widths = [font.getlength(letter) for letter in text]
    width = round(sum(widths) + tracking * (len(text) - 1))
    line = Image.new("L", (width + 4, round(size * 1.3)), 0)
    draw = ImageDraw.Draw(line)
    x = 2
    for letter, letter_width in zip(text, widths):
        draw.text((x, size * 0.05), letter, font=font, fill=255)
        x += letter_width + tracking
    return line


def gold(mask):
    width, height = mask.size
    fill = Image.new("RGB", (1, 2))
    fill.putpixel((0, 0), GOLD[0])
    fill.putpixel((0, 1), GOLD[1])
    return fill.resize((width, height), Image.BILINEAR)


def frame(shot, raw, out, size=None):
    """A frame at the size of the capture, or at the size that's given."""
    file, headline, _, _ = shot
    screen = Image.open(os.path.join(raw, file + ".png")).convert("RGB")
    size = size or screen.size
    width, height = size
    # sized by the narrower side, so that a tall phone and a squarer tablet set the words alike
    unit = min(width, height * 0.62)
    tablet = width / height > 0.6
    canvas = sky(size).convert("RGBA")

    letter = round(unit * 0.068)
    top = round(height * 0.052)
    y = top
    for index, text in enumerate(headline):
        tracking = letter * 0.06
        line = lettering(text.upper(), letter, tracking)
        if line.width > width * 0.9:
            sys.exit(f"{file}: \"{text}\" is wider than the frame")
        shadow = Image.new("RGBA", line.size, (6, 6, 26, 0))
        shadow.putalpha(line.filter(ImageFilter.GaussianBlur(letter * 0.16)).point(lambda value: round(value * 0.7)))
        at = ((width - line.width) // 2, y)
        canvas.alpha_composite(shadow, (at[0], at[1] + round(letter * 0.06)))
        color = gold(line) if index == len(headline) - 1 else Image.new("RGB", line.size, WHITE)
        canvas.paste(color, at, line)
        y += round(letter * 1.24)

    shown_width = round(width * (0.84 if tablet else 0.86))
    shown = screen.resize((shown_width, round(screen.height * shown_width / screen.width)), Image.LANCZOS)
    left, down = (width - shown_width) // 2, y + round(letter * 0.62)
    # the corners of the device, an iPad's are tighter
    radius = round(shown_width * (0.03 if tablet else 0.075))
    if down + shown.height < height:
        sys.exit(f"{file}: the screen ends above the bottom of the frame")
    shape = Image.new("L", shown.size, 0)
    ImageDraw.Draw(shape).rounded_rectangle((0, 0, shown.width - 1, shown.height + radius * 2), radius, fill=255)
    cast = Image.new("L", size, 0)
    cast.paste(shape, (left, down + round(unit * 0.02)))
    night = Image.new("RGBA", size, (4, 4, 22, 0))
    night.putalpha(cast.filter(ImageFilter.GaussianBlur(unit * 0.03)).point(lambda value: round(value * 0.75)))
    canvas.alpha_composite(night)
    canvas.paste(shown, (left, down), shape)
    edge = max(2, round(unit * 0.004))
    ImageDraw.Draw(canvas).rounded_rectangle((left - edge, down - edge, left + shown_width + edge - 1, height + radius * 2), radius + edge,
                                             outline=RING + (255,), width=edge)
    os.makedirs(out, exist_ok=True)
    framed = canvas.convert("RGB")
    framed.save(os.path.join(out, file + ".png"))
    if size == LARGE:
        os.makedirs(out + "-6.3", exist_ok=True)
        framed.resize(MEDIUM, Image.LANCZOS).save(os.path.join(out + "-6.3", file + ".png"))


def main():
    arguments = sys.argv[1:]
    if len(arguments) == 3 and arguments[0] == "capture":
        capture(arguments[1], arguments[2])
    elif len(arguments) == 3 and arguments[0] == "home":
        capture_home(arguments[1], arguments[2])
    elif len(arguments) == 3 and arguments[0] == "mac":
        capture_mac(arguments[1])
        for shot in SHOTS:
            frame_mac(shot, arguments[1], arguments[2].rstrip("/"))
    elif len(arguments) == 3 and arguments[0] == "watch":
        capture_watch(arguments[1], arguments[2])
    elif len(arguments) == 3 and arguments[0] == "vision":
        capture_vision(arguments[1], arguments[2])
    elif len(arguments) == 3 and arguments[0] == "frame":
        for shot in SHOTS:
            frame(shot, arguments[1], arguments[2].rstrip("/"))
        print(f"{len(SHOTS)} frames written to {arguments[2]}")
    else:
        sys.exit(__doc__)


if __name__ == "__main__":
    main()
