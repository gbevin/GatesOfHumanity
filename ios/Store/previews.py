#!/usr/bin/env python3
"""Makes the app previews of the App Store: records the app in a simulator while a script taps
and scrolls its pages, then cuts the scenes of each preview together, each above its two lines of
words and framed so that they're clear of what matters in it, and ends on the icon and the name
of the app. A scene fades into the next one.

    ios/Store/previews.py capture "<simulator name>" RAW [scene ...]   the scenes, as the simulator shows them
    ios/Store/previews.py cut RAW OUT WIDTHxHEIGHT                     each preview, at the size the store asks for

`capture` installs the app that `ios/build.sh` built, in a simulator that it boots and shuts down
again unless it was running already, and fills it with the people of the screenshots. A preview
is 886x1920 for an iPhone and 1200x1600 for an iPad, lasts 15 to 30 seconds and has a sound
track, which is silence here. The scenes of an iPad are other recordings than those of an
iPhone, its pages are laid out in another way. Needs Pillow and ffmpeg."""
import json
import os
import re
import signal
import subprocess
import sys
import time

from PIL import Image, ImageDraw

from screenshots import APP, BUNDLE, WHITE, gold, home, lettering, run, shown, simulator, sky, stored

ICON = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "app", "Assets.xcassets", "AppIcon.appiconset", "icon1024.png")
NAME = ["Gates of Humanity", "Human Design Charts & Transits"]
RATE = 30
# What a scene stands still for before its script begins and how long it takes to fade into the
# next one. Two charts that are nearly at the same place fading into each other look like a
# fault, so where a scene ends on a chart and the next one begins on one, they're of quite
# another size, and what happens in one page is one scene.
LEAD, FADE = 0.2, 0.35
# Words that only fade into other words are easy to miss, the picture seems to stay as it was.
# So they leave to the left and the next ones come from the right, across what moves in the
# scenes, which is up and down, the second line a moment after the first: how far they travel
# as a share of the width, how long leaving and coming take, how long after the leaving begins
# the coming does, and how much later the second line is.
TRAVEL, LEAVES, COMES, AFTER_LEAVING, SECOND = 0.2, 0.22, 0.42, 0.2, 0.09
# how long the icon is there at the end
CARD = 2.5
# The words of a scene have the bottom of a preview for themselves, and a scene the rest: that
# much of the height of the screen, from where the scene says, so that the chart, what changes
# and what's tapped are above the words. The night of the words begins a little above them.
# On an iPad the chart of the transits and the date above it take nine tenths of the screen,
# down to where the night begins, and the letters, a share of the width, are smaller on its
# wider picture.
SHOWN, LETTER, DUSK = {"phone": 0.85, "tablet": 0.9}, {"phone": 0.066, "tablet": 0.042}, {"phone": 0.025, "tablet": 0.012}


def device(size):
    return "tablet" if size[0] / size[1] > 0.6 else "phone"


# What every scene's script starts with: a finger that shows where it taps and drags, a camera
# whose moves the cut makes, and a screen of one color that the recording is cut at.
BEFORE = """
const pause = ms => new Promise(resolve => setTimeout(resolve, ms));
const tablet = innerWidth > 700;
const finger = document.createElement('div');
finger.style.cssText = 'position:fixed;z-index:2147483647;left:0;top:0;width:46px;height:46px;margin:-23px 0 0 -23px;border-radius:50%;'
    + 'background:rgba(255,255,255,0.6);border:2px solid rgba(33,33,78,0.6);box-shadow:0 2px 12px rgba(0,0,0,0.4);pointer-events:none;'
    + 'opacity:0;transform:scale(1.7);transition:opacity 0.14s, transform 0.14s';
document.documentElement.appendChild(finger);
const place = (x, y) => { finger.style.left = x + 'px'; finger.style.top = y + 'px'; };
const down = async (x, y, ms) => { place(x, y); finger.style.opacity = 1; finger.style.transform = 'scale(1)'; await pause(ms); };
const up = async ms => { finger.style.opacity = 0; finger.style.transform = 'scale(1.7)'; await pause(ms); };
const found = target => {
    const element = typeof target === 'string' ? document.querySelector(target) : target;
    if (!element) throw new Error('nothing there: ' + target);
    return element;
};
const tap = async (target, quick = false) => {
    const element = found(target);
    const box = element.getBoundingClientRect();
    const x = box.x + box.width / 2, y = box.y + box.height / 2;
    await down(x, y, quick ? 150 : 280);
    element.dispatchEvent(new MouseEvent('click', {bubbles: true, cancelable: true, clientX: x, clientY: y}));
    await up(quick ? 150 : 220);
};
const part = name => found([...document.querySelectorAll('#svg-image svg [data-explore]')].find(each => each.dataset.explore.startsWith(name)));
// the channel that's drawn highest of those the page describes: the card that a tap opens
// covers the lower half of the chart, and what was tapped should stay in view above it
const highest = () => [...document.querySelectorAll('#explore details[data-part^="channel."]')].map(each => part(each.dataset.part))
    .sort((one, other) => one.getBoundingClientRect().bottom - other.getBoundingClientRect().bottom)[0];
// a finger that flicks what scrolls, which goes on a little after it lets go: in the app a page
// scrolls inside the window
const page = document.documentElement.classList.contains('in-app') ? document.body : document.scrollingElement;
const flick = async (distance, ms = 1000, scroller = page) => {
    const box = scroller === page ? {y: 0, height: innerHeight} : scroller.getBoundingClientRect();
    const x = innerWidth * 0.64, from = box.y + box.height * (distance > 0 ? 0.72 : 0.32);
    const travel = Math.sign(distance) * Math.min(Math.abs(distance), box.height * 0.36);
    await down(x, from, 200);
    const start = scroller.scrollTop, began = performance.now();
    await new Promise(done => {
        const step = now => {
            const at = Math.min(1, (now - began) / ms);
            scroller.scrollTop = start + distance * (1 - Math.pow(1 - at, 3));
            const lift = Math.min(1, at / 0.3);
            place(x, from - travel * lift);
            if (lift === 1) finger.style.opacity = 0;
            at < 1 ? requestAnimationFrame(step) : done();
        };
        requestAnimationFrame(step);
    });
    finger.style.transform = 'scale(1.7)';
};
// the camera comes closer to what it's pointed at, or goes back to the whole screen without one;
// a move at a moment before the scene is where the camera is when the scene begins
const moves = [];
const camera = (zoom, target, ms = 1200, at = Date.now() - began) => {
    let x = 0.5, y = 0.5;
    if (target) {
        const box = found(target).getBoundingClientRect();
        // the page begins under the status bar, which has what the screen is taller by
        const above = screen.height - innerHeight;
        x = (box.x + box.width / 2) / innerWidth;
        y = (above + Math.min(box.y + box.height / 2, innerHeight)) / screen.height;
    }
    moves.push({at: at, zoom: zoom, x: x, y: y, ms: ms});
};
// the next words of the scene come under it
const told = [];
const say = () => told.push(Date.now() - began);
"""
# A script can have what it prepares before this, above a line that says @scene.
MARK = """
const mark = document.createElement('div');
mark.style.cssText = 'position:fixed;inset:0;z-index:2147483647;background:#ff00ff';
document.documentElement.appendChild(mark);
await pause(300);
mark.remove();
await pause(700);
const began = Date.now();
"""
AFTER = """
return 'lasted ' + (Date.now() - began) + ' words ' + JSON.stringify(told) + ' camera ' + JSON.stringify(moves);
"""

# The previews in the order of the store, each with its scenes: the name of the recording, the
# two lines of words under it, more than once when its script says when the next ones come, how
# much of the screen is left out at its top on a phone and on a tablet, at most what the words
# take, the page that the app starts at and what's done in it. The scene of a chart begins above
# the row of its date. On a phone a page is moved up before a tap opens the card, which covers
# the lower half of its screen, by what takes the fields above the chart just out of the scene;
# on a tablet the card leaves the chart in view. The transits and the connection don't follow
# each other: their pages are nearly the same, with the wheel a little apart and other rows of
# fields above it, so one of the two always shows double.
PREVIEWS = [
    ("1-gates", [
        # It begins close to a chart that moves: the days before the birth, one after the other, which
        # ends on the chart of the scene. Those three weeks change more of the body graph than nine
        # tenths of the stretches of that length around them. It's the one place where a chart runs
        # through time: a second run, in the scene of the transits, is one too many.
        ("birth", [["Your Human Design", "birth chart"], ["Tap any part", "to read what it is"]], (0.125, 0.0756), "/wheel/birth", """
            // a tablet draws its larger chart a little slower, and a preview has thirty seconds
            const days = tablet ? 18 : 22, birth = found('#datetime').value;
            const drawn = () => new Promise(resolve => {
                const seen = new MutationObserver(() => { seen.disconnect(); resolve(); });
                seen.observe(found('#svg-image'), {childList: true});
                setTimeout(() => { seen.disconnect(); resolve(); }, 1500);
            });
            const earlier = drawn();
            showDate(getDateOffset(-86400 * days), image_url);
            await earlier;
            @scene
            camera(1.25, '#svg-image', 1, -1000);
            await pause(350);
            for (let day = 0; day < days; day++) {
                const next = drawn();
                found('#day-plus').click();
                await Promise.all([next, pause(85)]);
            }
            if (found('#datetime').value !== birth) throw new Error('the days ended on ' + found('#datetime').value);
            await pause(450);
            camera(1, null, 1300);
            await pause(2000);
            say();
            if (tablet) await pause(900); else await flick(130, 700);
            await pause(300);
            await tap(highest());
            await pause(2300);
        """),
        # the full screen view begins a step closer: at its first size the chart is nearly as on
        # the page, a little larger and higher
        ("closer", [["A closer look", "at every detail"]], (0.05, 0.0233), "/wheel/birth", """
            found('#chart-zoom').click();
            await pause(500);
            found('#chart-viewer-in').click();
            await pause(600);
            @scene
            await pause(900);
            await tap('#chart-viewer-in');
            await pause(900);
            await tap('#chart-viewer-in');
            await pause(1400);
        """),
        ("transits", [["The transits", "of any moment"]], (0.125, 0.0756), "/wheel/birth-transit", """
            await pause(500);
            for (let day = 0; day < 4; day++) {
                await tap('#day-plus', true);
                await pause(130);
            }
            await pause(700);
        """),
        ("learn", [["Learn Human Design", "a topic at a time"]], (0.06, 0.0233), "/learn", """
            await pause(900);
            await flick(tablet ? 400 : 640, 1100);
            await pause(1100);
        """),
        ("connection", [["See what connects", "two people"]], (0.125, 0.0756), "/wheel/connection", """
            await pause(1500);
            if (tablet) await pause(700); else await flick(262, 900);
            await pause(400);
            await tap(highest());
            await pause(1900);
        """),
        # not a page of the app: its widgets on the Home Screen, which stand still for as many seconds
        ("widgets", [["Widgets for what's", "happening now"]], (0.0, 0.0), None, 2.4),
    ]),
]


def scenes(names=()):
    every = [scene for _, parts in PREVIEWS for scene in parts]
    unknown = set(names) - {scene[0] for scene in every}
    if unknown:
        sys.exit("no scene is called " + ", ".join(sorted(unknown)))
    return [scene for scene in every if not names or scene[0] in names]


def capture(name, raw, names):
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
        for file, _, _, start, script in scenes(names):
            film = os.path.join(raw, file + ".mov")
            if start is None:
                subprocess.run(["xcrun", "simctl", "terminate", udid, BUNDLE], capture_output=True)
                still = os.path.join(raw, file + ".png")
                home(name, udid, still)
                run("ffmpeg", "-v", "error", "-y", "-loop", "1", "-framerate", str(RATE), "-t", f"{script + 1:.1f}", "-i", still,
                    "-vf", "scale=trunc(iw/2)*2:trunc(ih/2)*2", "-c:v", "libx264", "-pix_fmt", "yuv420p", film)
                with open(os.path.join(raw, file + ".json"), "w") as noted:
                    json.dump({"begins": 0, "lasts": script, "words": [], "camera": []}, noted)
                print(f"{name}: {file}, {script:.1f} s of the Home Screen")
                continue
            recorder = subprocess.Popen(["xcrun", "simctl", "io", udid, "recordVideo", "--codec", "h264", "--force", film],
                                        stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
            try:
                prepared, _, played = script.rpartition("@scene")
                said = shown(udid, start, BEFORE + prepared + MARK + played + AFTER)
                time.sleep(0.6)
            finally:
                recorder.send_signal(signal.SIGINT)
                recorder.wait()
            told = re.search(r"lasted (\d+) words (\[.*?\]) camera (\[.*\])\)", said)
            if not told:
                sys.exit(f"{file}: {said}")
            lasted = int(told.group(1)) / 1000
            # the script waits 0.7 seconds after its mark went away
            begins = marked(film) + 0.7 - LEAD
            with open(os.path.join(raw, file + ".json"), "w") as noted:
                json.dump({"begins": round(begins, 3), "lasts": round(lasted + LEAD, 3), "words": json.loads(told.group(2)),
                           "camera": json.loads(told.group(3))}, noted)
            print(f"{name}: {file}, {lasted:.1f} s from {begins:.1f} s of the recording")
        subprocess.run(["xcrun", "simctl", "terminate", udid, BUNDLE], capture_output=True)
        run("xcrun", "simctl", "status_bar", udid, "clear")
    finally:
        if not booted:
            subprocess.run(["xcrun", "simctl", "shutdown", udid], check=True)


def marked(film):
    """When the screen of one color that a script begins with went away, in seconds."""
    middle = subprocess.run(["ffmpeg", "-v", "error", "-i", film, "-vf", f"fps={RATE},crop=32:32:(iw-32)/2:(ih-32)/2,scale=1:1",
                             "-f", "rawvideo", "-pix_fmt", "rgb24", "-"], check=True, capture_output=True).stdout
    last = None
    for index in range(len(middle) // 3):
        red, green, blue = middle[index * 3:index * 3 + 3]
        if red > 200 and green < 90 and blue > 200:
            last = index
    if last is None:
        sys.exit(f"{film}: the mark of the script isn't in the recording")
    return (last + 1) / RATE


def night_under(size, file):
    """The night of the icon where the words of a scene are, with nothing where the scene is."""
    width, height = size
    night = sky(size).crop((0, height - 1, width, height)).resize(size).convert("RGBA")
    rise = Image.new("L", (1, height), 0)
    for y in range(height):
        rise.putpixel((0, y), round(255 * min(1.0, max(0.0, (y / height - (SHOWN[device(size)] - DUSK[device(size)])) / DUSK[device(size)]))))
    night.putalpha(rise.resize(size))
    night.save(file)


def line_under(words, line, size, file):
    """One of the two lines of a scene, the second one in the gold of the icon."""
    width, height = size
    layer = Image.new("RGBA", size, (0, 0, 0, 0))
    shown, letter = SHOWN[device(size)], round(width * LETTER[device(size)])
    drawn = lettering(words[line].upper(), letter, letter * 0.06)
    if drawn.width > width * 0.94:
        sys.exit(f"\"{words[line]}\" is wider than the preview")
    color = gold(drawn) if line == len(words) - 1 else Image.new("RGB", drawn.size, WHITE)
    layer.paste(color, ((width - drawn.width) // 2, round(height * (shown + (1 - shown) / 2) + letter * 1.3 * (line - 1))), drawn)
    layer.save(file)


def card(size, file):
    """The icon and what the app is called on its sky."""
    width, height = size
    picture = sky((width, height)).convert("RGBA")
    side = round(width * 0.4)
    icon = Image.open(ICON).convert("RGBA").resize((side, side), Image.LANCZOS)
    rounded = Image.new("L", (side, side), 0)
    ImageDraw.Draw(rounded).rounded_rectangle((0, 0, side - 1, side - 1), round(side * 0.225), fill=255)
    top = round(height * 0.3)
    picture.paste(icon, ((width - side) // 2, top), rounded)
    letter = round(width * 0.062)
    name = Image.new("RGBA", (width, round(letter * 3)), (0, 0, 0, 0))
    for index, (text, scale) in enumerate(zip(NAME, (1.0, 0.56))):
        line = lettering(text.upper(), round(letter * scale), letter * scale * 0.08)
        color = gold(line) if index else Image.new("RGB", line.size, WHITE)
        name.paste(color, ((width - line.width) // 2, round(index * letter * 1.45)), line)
    picture.alpha_composite(name, (0, top + side + round(letter * 1.1)))
    picture.convert("RGB").save(file)


def moved(camera, value, rest, within=lambda at: at):
    """What the camera has for a value at each moment: every move adds its share, eased at both ends."""
    said, last = str(rest), rest
    for move in camera:
        at = f"clip((on/{RATE}-{move['at'] / 1000 + LEAD:.3f})/{max(move['ms'], 1) / 1000:.3f},0,1)"
        said += f"+({within(move[value]) - last:.4f})*pow({at},2)*(3-2*{at})"
        last = within(move[value])
    return said


def cut(raw, out, size):
    width, height = size
    shown, tablet = SHOWN[device(size)], device(size) == "tablet"
    tall = round(height * shown / 2) * 2
    os.makedirs(out, exist_ok=True)
    icon, night = os.path.join(raw, "card.png"), os.path.join(raw, "night.png")
    card(size, icon)
    night_under(size, night)
    for name, parts in PREVIEWS:
        inputs, filters = [], []

        def still(picture, lasts):
            inputs.extend(["-loop", "1", "-framerate", str(RATE), "-t", f"{lasts:.3f}", "-i", picture])
            return inputs.count("-i") - 1

        # the scenes one after the other, each fading into the next, and when the words under them come
        joined, at, said = None, 0.0, []
        for index, (file, words, aboves, _, _) in enumerate(parts):
            above = aboves[tablet]
            if not 0 <= above <= 1 - shown + 0.0001:
                sys.exit(f"{file} leaves out more of the screen than the words take")
            with open(os.path.join(raw, file + ".json")) as noted:
                note = json.load(noted)
            lasts = note["lasts"]
            inputs.extend(["-i", os.path.join(raw, file + ".mov")])
            # a recording only has a frame when the screen changed: every thirtieth of a second gets
            # one, also after the last, before the scene is taken out of it
            scene = (f"[{inputs.count('-i') - 1}:v]fps={RATE},tpad=stop_mode=clone:stop_duration=30,"
                     f"trim=start={note['begins']}:duration={lasts},setpts=PTS-STARTPTS,"
                     f"crop=iw:trunc(ih*{shown}/2)*2:0:trunc(ih*{above}),")
            if note["camera"]:
                # the camera was pointed at a place on the whole screen
                def shown_at(y):
                    return (y - above) / shown
                scene += (f"scale=iw*2:ih*2:flags=bicubic,zoompan=z='{moved(note['camera'], 'zoom', 1)}':"
                          f"x='({moved(note['camera'], 'x', 0.5)})*iw-iw/zoom/2':y='({moved(note['camera'], 'y', 0.5, shown_at)})*ih-ih/zoom/2':"
                          f"d=1:s={width}x{tall}:fps={RATE},")
            else:
                # as the camera shows the whole of it, so that a scene with a camera can go on in one without
                scene += f"scale={width}:{tall}:flags=lanczos,"
            filters.append(scene + f"pad={width}:{height}:0:0,fps={RATE},setsar=1,format=yuv420p[film{index}]")
            if len(words) != len(note.get("words", [])) + 1:
                sys.exit(f"{file} has {len(words)} times two lines and says {len(note.get('words', []))} times when the next come")
            begins = 0.0 if joined is None else at - FADE
            if joined is None:
                joined = f"film{index}"
            else:
                filters.append(f"[{joined}][film{index}]xfade=transition=fade:duration={FADE}:offset={begins:.3f}[joined{index}]")
                joined = f"joined{index}"
            said += zip([begins] + [begins + told / 1000 + LEAD for told in note.get("words", [])], words, [f"{file}-words{each}" for each in range(len(words))])
            at = begins + lasts

        filters.append(f"[{joined}][{still(night, at)}:v]overlay=shortest=1[under0]")
        layers = 0
        for index, (comes, words, file) in enumerate(said):
            goes = said[index + 1][0] if index + 1 < len(said) else None
            for line in range(len(words)):
                drawn = os.path.join(raw, f"{file}-{line}.png")
                line_under(words, line, size, drawn)
                rises = comes + (AFTER_LEAVING if index else 0) + line * SECOND
                shown_ = f"fade=t=in:st={rises:.3f}:d={COMES}:alpha=1"
                way = f"{width * TRAVEL:.1f}*pow(1-clip((t-{rises:.3f})/{COMES},0,1),3)"
                if goes is not None:
                    shown_ += f",fade=t=out:st={goes:.3f}:d={LEAVES}:alpha=1"
                    way += f"-{width * TRAVEL:.1f}*pow(clip((t-{goes:.3f})/{LEAVES},0,1),2)"
                filters.append(f"[{still(drawn, at)}:v]format=rgba,{shown_}[line{layers}]")
                filters.append(f"[under{layers}][line{layers}]overlay=x='{way}':y=0:shortest=1[under{layers + 1}]")
                layers += 1
        filters.append(f"[under{layers}]fps={RATE},setsar=1,format=yuv420p[worded]")
        filters.append(f"[{still(icon, CARD)}:v]fps={RATE},setsar=1,format=yuv420p[closing]")
        filters.append(f"[worded][closing]xfade=transition=fade:duration={FADE}:offset={at - FADE:.3f}[whole]")
        at += CARD - FADE
        if not 15 <= at <= 30:
            sys.exit(f"{name} would last {at:.1f} seconds, a preview lasts 15 to 30")
        film = os.path.join(out, name + ".mp4")
        run("ffmpeg", "-v", "error", "-y", *inputs, "-f", "lavfi", "-t", f"{at:.3f}", "-i", "anullsrc=r=48000:cl=stereo",
            "-filter_complex", ";".join(filters), "-map", "[whole]", "-map", f"{inputs.count('-i')}:a",
            "-c:v", "libx264", "-profile:v", "high", "-level:v", "4.0", "-pix_fmt", "yuv420p", "-r", str(RATE),
            "-b:v", "10M", "-maxrate", "12M", "-bufsize", "20M", "-c:a", "aac", "-b:a", "256k", "-movflags", "+faststart", film)
        print(f"{film}: {at:.1f} seconds")


def main():
    arguments = sys.argv[1:]
    if len(arguments) >= 3 and arguments[0] == "capture":
        capture(arguments[1], arguments[2], arguments[3:])
    elif len(arguments) == 4 and arguments[0] == "cut" and arguments[3].replace("x", "").isdigit():
        cut(arguments[1], arguments[2].rstrip("/"), tuple(int(side) for side in arguments[3].split("x")))
    else:
        sys.exit(__doc__)


if __name__ == "__main__":
    main()
