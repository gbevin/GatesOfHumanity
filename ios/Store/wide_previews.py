#!/usr/bin/env python3
"""Makes the app previews of the App Store for the systems whose picture is wider than high: the
app for visionOS, in the room of the simulator, and the Mac app, as a window on the sky of the
icon. The scenes, their words and how they follow each other are those of `previews.py`, with
what each system has of its own.

    ios/Store/wide_previews.py vision "<simulator name> Capture" RAW [scene ...]   records the scenes in the simulator
    ios/Store/wide_previews.py mac RAW [scene ...]                                 records them in the Mac app
    ios/Store/wide_previews.py cut vision|mac RAW OUT                              the preview, as the store asks for it

`vision` records the app that `vision/build.sh` built, in a simulator that was made for
captures, see `screenshots.py`. `mac` records the Mac app that's built to be looked into
(`GATES_PROBED=1 mac/build.sh app`), which films what its own window shows. A preview is
3840x2160 for visionOS and 1920x1080 for a Mac, and lasts 15 to 30 seconds. Needs Pillow and
ffmpeg."""
import json
import os
import re
import signal
import subprocess
import sys
import time

from PIL import Image, ImageDraw, ImageFilter

from previews import AFTER, AFTER_LEAVING, BEFORE, CARD, COMES, FADE, ICON, LEAD, LEAVES, MARK, NAME, RATE, SECOND
from screenshots import (BUNDLE, CAPTURE, EDGE, FRESH, MAC_APP, MAC_BAR, MAC_BUTTONS, MAC_WINDOW, QUIET, RING, VISION_APP, WHITE, gold, lettering,
                         roomed, run, shown, simulator, sky, stored)

# How far the words travel when they leave and come, as a share of the width: less than on a
# phone, whose picture is all theirs to cross.
TRAVEL = 0.06
SIZES = {"vision": (3840, 2160), "mac": (1920, 1080)}
WINDOW = "1000x1120"

# The scenes of each system: the name of the recording, the two lines of words under it, more
# than once when its script says when the next ones come, the page that the app starts at, for
# visionOS whether the chart is in the room beside the window, and what's done in the page. A
# scene with a chart doesn't follow one with nearly the same chart, as in previews.py.
RUN = """
    const days = %d, birth = found('#datetime').value;
    const drawn = () => new Promise(resolve => {
        const seen = new MutationObserver(() => { seen.disconnect(); resolve(); });
        seen.observe(found('#svg-image'), {childList: true});
        pause(1500).then(() => { seen.disconnect(); resolve(); });
    });
    const earlier = drawn();
    showDate(getDateOffset(-86400 * days), image_url);
    await earlier;
    @scene
    await pause(350);
    for (let day = 0; day < days; day++) {
        const next = drawn();
        found('#day-plus').click();
        await Promise.all([next, pause(%d)]);
    }
    if (found('#datetime').value !== birth) throw new Error('the days ended on ' + found('#datetime').value);
    await pause(1500);
    say();
    await pause(500);
    await tap(highest());
    await pause(2600);
"""
SCENES = {
    "vision": [
        ("birth", [["Your Human Design", "birth chart"], ["Tap any part", "to read what it is"]], "/wheel/birth", None, RUN % (18, 85)),
        # the chart in the room is built while the page waits, and slides from one day to the next
        ("room", [["The chart", "in your room"]], "/wheel/birth", "nothing", """
            await pause(3000);
            @scene
            await pause(1400);
            for (let day = 0; day < 5; day++) {
                found('#day-plus').click();
                await pause(900);
            }
            await pause(700);
        """),
        ("learn", [["Learn Human Design", "a topic at a time"]], "/learn", None, """
            await pause(900);
            await flick(420, 1100);
            await pause(1100);
        """),
        ("connection", [["See what connects", "two people"]], "/wheel/connection", None, """
            await pause(1500);
            await tap(highest());
            await pause(2300);
        """),
    ],
    "mac": [
        ("birth", [["Your Human Design", "birth chart"], ["Tap any part", "to read what it is"]], "/wheel/birth", None, RUN % (18, 200)),
        ("closer", [["A closer look", "at every detail"]], "/wheel/birth", None, """
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
        ("transits", [["The transits", "of any moment"]], "/wheel/birth-transit", None, """
            await pause(500);
            for (let day = 0; day < 4; day++) {
                await tap('#day-plus', true);
                await pause(130);
            }
            await pause(700);
        """),
        ("learn", [["Learn Human Design", "a topic at a time"]], "/learn", None, """
            await pause(900);
            await flick(400, 1100);
            await pause(1100);
        """),
        ("connection", [["See what connects", "two people"]], "/wheel/connection", None, """
            await pause(1500);
            await tap(highest());
            await pause(2300);
        """),
    ],
}


# A window of a Mac that's behind other windows, or whose screen is locked, isn't asked to draw
# and gets to act once a second at most, so a script there would crawl and what it animates
# would wait without end. Asking the site for something isn't held back: a script waits by
# asking until the time is over, and what it animates goes by that clock, in a scene that
# flicks.
SPIN = "const pause = async ms => { const until = performance.now() + ms; while (performance.now() < until) await fetch('/robots.txt', {cache: 'no-store'}); };"
STEADY = "window.requestAnimationFrame = step => { (async () => { const until = performance.now() + 16; while (performance.now() < until) await fetch('/robots.txt', {cache: 'no-store'}); step(performance.now()); })(); };"
TIMED = "const pause = ms => new Promise(resolve => setTimeout(resolve, ms));"


def scenes(system, names):
    unknown = set(names) - {scene[0] for scene in SCENES[system]}
    if unknown:
        sys.exit("no scene is called " + ", ".join(sorted(unknown)))
    return [scene for scene in SCENES[system] if not names or scene[0] in names]


def played(script, before=BEFORE):
    prepared, _, rest = script.rpartition("@scene")
    return before + prepared + MARK + rest + AFTER


def noted(raw, file, film, said):
    told = re.search(r"lasted (\d+) words (\[.*?\]) camera (\[.*\])\)", said)
    if not told:
        sys.exit(f"{file}: {said}")
    lasted = int(told.group(1)) / 1000
    # the script waits 0.7 seconds after its mark went away
    begins = marked(film) + 0.7 - LEAD
    with open(os.path.join(raw, file + ".json"), "w") as written:
        json.dump({"begins": round(begins, 3), "lasts": round(lasted + LEAD, 3), "words": json.loads(told.group(2))}, written)
    print(f"{file}, {lasted:.1f} s from {begins:.1f} s of the recording")


def marked(film):
    """When the screen of one color that a script begins with went away, in seconds. In a room
    the window is glass and paler when something else is looked at, so it's the color that
    counts and not how deep it is."""
    middle = subprocess.run(["ffmpeg", "-v", "error", "-i", film, "-vf", f"fps={RATE},crop=32:32:(iw-32)/2:(ih-32)/2,scale=1:1",
                             "-f", "rawvideo", "-pix_fmt", "rgb24", "-"], check=True, capture_output=True).stdout
    last = None
    for index in range(len(middle) // 3):
        red, green, blue = middle[index * 3:index * 3 + 3]
        if red > 130 and blue > 130 and green < min(red, blue) - 60:
            last = index
    if last is None:
        sys.exit(f"{film}: the mark of the script isn't in the recording")
    return (last + 1) / RATE


def capture_vision(name, raw, names):
    if not name.endswith(CAPTURE):
        sys.exit(f"{name} isn't a simulator made for captures, whose name ends with{CAPTURE}")
    if not os.path.isdir(VISION_APP):
        sys.exit("vision/build.sh hasn't built the app for the simulator yet")
    os.makedirs(raw, exist_ok=True)
    udid, booted = simulator(name)
    if not booted:
        subprocess.run(["xcrun", "simctl", "boot", udid], check=True)
    try:
        roomed(udid)
        run("xcrun", "simctl", "install", udid, VISION_APP)
        shown(udid, "/about", "localStorage.setItem('hdapp', " + json.dumps(json.dumps(stored())) + "); return 'filled';", GATES_SIZE=WINDOW)
        for file, _, start, room, script in scenes("vision", names):
            film = os.path.join(raw, file + ".mov")
            recorder = subprocess.Popen(["xcrun", "simctl", "io", udid, "recordVideo", "--codec", "h264", "--force", film],
                                        stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
            try:
                said = shown(udid, start, played(script), GATES_SIZE=WINDOW, **({"GATES_VOLUME": room} if room else {}))
                time.sleep(0.6)
            finally:
                recorder.send_signal(signal.SIGINT)
                recorder.wait()
            noted(raw, file, film, said)
        subprocess.run(["xcrun", "simctl", "terminate", udid, BUNDLE], capture_output=True)
    finally:
        if not booted:
            subprocess.run(["xcrun", "simctl", "shutdown", udid], check=True)


def capture_mac(raw, names):
    if not os.path.exists(MAC_APP) or b".probed" not in subprocess.run(["codesign", "-d", "-v", MAC_APP], capture_output=True).stderr:
        sys.exit("the Mac app isn't one that's built to be looked into: GATES_PROBED=1 mac/build.sh app")
    os.makedirs(raw, exist_ok=True)

    def started(start, script, **probes):
        environment = dict(os.environ, GATES_START=start, GATES_EVAL=script, GATES_SIZE=MAC_WINDOW, **probes)
        return subprocess.run([MAC_APP, *FRESH], env=environment, capture_output=True, text=True, timeout=180).stdout.split("\n")

    started("/about", "localStorage.setItem('hdapp', " + json.dumps(json.dumps(stored())) + "); return 'filled';")
    for file, _, start, _, script in scenes("mac", names):
        # a page that asks the site for a chart at every step is left alone longer between two pictures
        if TIMED not in BEFORE:
            sys.exit("the scripts of previews.py wait in another way than this replaces")
        flicks = "flick" in script
        # a page that asks the site for a chart at every step is left alone longer between two pictures
        said = started(start, QUIET + (STEADY if flicks else "") + played(script, BEFORE.replace(TIMED, SPIN)), GATES_FILM="20" if flicks else "110")
        probe = next((line for line in said if line.startswith("probe ")), "the app never said what its script returned")
        folder = next((line[5:] for line in said if line.startswith("film ")), None)
        if folder is None or "failure" in probe:
            sys.exit(f"{file}: {probe}")
        # The app wrote a picture whenever it had one, named after its moment: each is shown
        # until the next one.
        pictures = sorted(each for each in os.listdir(folder) if each.endswith(".jpg"))
        listed = os.path.join(raw, file + ".txt")
        with open(listed, "w") as written:
            for picture, following in zip(pictures, pictures[1:] + [None]):
                lasts = (int(following[:-4]) - int(picture[:-4])) / 1000 if following else 1 / RATE
                written.write(f"file '{os.path.join(folder, picture)}'\nduration {lasts:.3f}\n")
        film = os.path.join(raw, file + ".mov")
        run("ffmpeg", "-v", "error", "-y", "-f", "concat", "-safe", "0", "-i", listed, "-vf", f"fps={RATE},scale=trunc(iw/2)*2:trunc(ih/2)*2",
            "-c:v", "libx264", "-crf", "12", "-pix_fmt", "yuv420p", film)
        os.remove(listed)
        noted(raw, file, film, probe)


def card(size, file):
    """The icon and what the app is called on its sky."""
    width, height = size
    picture = sky(size).convert("RGBA")
    side = round(height * 0.36)
    icon = Image.open(ICON).convert("RGBA").resize((side, side), Image.LANCZOS)
    rounded = Image.new("L", (side, side), 0)
    ImageDraw.Draw(rounded).rounded_rectangle((0, 0, side - 1, side - 1), round(side * 0.225), fill=255)
    top = round(height * 0.2)
    picture.paste(icon, ((width - side) // 2, top), rounded)
    letter = round(height * 0.075)
    y = top + side + round(letter * 0.9)
    for index, (text, scale) in enumerate(zip(NAME, (1.0, 0.56))):
        line = lettering(text.upper(), round(letter * scale), letter * scale * 0.08)
        picture.paste(gold(line) if index else Image.new("RGB", line.size, WHITE), ((width - line.width) // 2, y), line)
        y += round(letter * 1.45)
    picture.convert("RGB").save(file)


def window(size):
    """Where the Mac app's window is in its preview, as in the frames of its screenshots: the
    whole of it, and what it shows under its bar."""
    width, height = size
    across, down = (int(side) for side in MAC_WINDOW.split("x"))
    tall = round(height * 0.86 / 2) * 2
    scale = tall / (down + MAC_BAR)
    wide = round(across * scale / 2) * 2
    bar = round(MAC_BAR * scale)
    left, top = width - wide - round(width * 0.07), (height - tall) // 2
    return (left, top, wide, tall), (left, top + bar, wide, tall - bar), scale


def around(size, file):
    """The sky around the window of the Mac app, with the bar of the window and its round
    corners, and nothing where the window shows the app."""
    width, height = size
    (left, top, wide, tall), (_, under, _, _), scale = window(size)
    radius = round(10 * scale)
    canvas = sky(size).convert("RGBA")
    shape = Image.new("L", size, 0)
    ImageDraw.Draw(shape).rounded_rectangle((left, top, left + wide - 1, top + tall - 1), radius, fill=255)
    cast = Image.new("L", size, 0)
    cast.paste(shape, (0, round(height * 0.02)))
    night = Image.new("RGBA", size, (4, 4, 22, 0))
    night.putalpha(cast.filter(ImageFilter.GaussianBlur(height * 0.03)).point(lambda value: round(value * 0.75)))
    canvas.alpha_composite(night)
    draw = ImageDraw.Draw(canvas)
    draw.rounded_rectangle((left - 2, top - 2, left + wide + 1, top + tall + 1), radius + 2, outline=RING + (255,), width=2)
    draw.rounded_rectangle((left, top, left + wide - 1, top + tall - 1), radius, fill=EDGE + (255,))
    for index, color in enumerate(MAC_BUTTONS):
        x, y, r = left + (20 + index * 20) * scale, top + MAC_BAR * scale / 2, 6 * scale
        draw.ellipse((x - r, y - r, x + r, y + r), fill=color + (255,))
    # what the window shows, with the round corners at its bottom
    hole = Image.new("L", size, 255)
    shown_ = Image.new("L", size, 0)
    ImageDraw.Draw(shown_).rounded_rectangle((left, top, left + wide - 1, top + tall - 1), radius, fill=255)
    ImageDraw.Draw(shown_).rectangle((0, 0, width, under - 1), fill=0)
    hole.paste(0, (0, 0), shown_)
    alpha = canvas.getchannel("A")
    canvas.putalpha(Image.composite(alpha, Image.new("L", size, 0), hole))
    canvas.save(file)


def line_at(words, line, size, middle, top, letter, file):
    """One of the two lines of a scene around a place, the second one in the gold of the icon."""
    layer = Image.new("RGBA", size, (0, 0, 0, 0))
    drawn = lettering(words[line].upper(), letter, letter * 0.06)
    at = (round(middle - drawn.width / 2), round(top + letter * 1.24 * line))
    shadow = Image.new("RGBA", drawn.size, (6, 6, 26, 0))
    shadow.putalpha(drawn.filter(ImageFilter.GaussianBlur(letter * 0.16)).point(lambda value: round(value * 0.8)))
    layer.alpha_composite(shadow, (at[0], at[1] + round(letter * 0.06)))
    layer.paste(gold(drawn) if line == len(words) - 1 else Image.new("RGB", drawn.size, WHITE), at, drawn)
    layer.save(file)


def cut(system, raw, out):
    size = SIZES[system]
    width, height = size
    os.makedirs(out, exist_ok=True)
    icon = os.path.join(raw, "card.png")
    card(size, icon)
    inputs, filters = [], []

    def still(picture, lasts):
        inputs.extend(["-loop", "1", "-framerate", str(RATE), "-t", f"{lasts:.3f}", "-i", picture])
        return inputs.count("-i") - 1

    if system == "mac":
        _, (left, under, wide, tall), _ = window(size)
        stage = (wide, tall)
        # the words beside the window, in the middle of the room that it leaves
        middle, letter = left / 2, round(height * 0.062)
        top = (height - letter * 1.24 * 2) / 2
    else:
        stage = size
        # the words over the top of the room, above the window
        middle, letter, top = width / 2, round(height * 0.05), height * 0.035

    joined, at, said = None, 0.0, []
    for index, (file, words, _, _, _) in enumerate(SCENES[system]):
        with open(os.path.join(raw, file + ".json")) as written:
            note = json.load(written)
        lasts = note["lasts"]
        inputs.extend(["-i", os.path.join(raw, file + ".mov")])
        # a recording only has a frame when the screen changed: every thirtieth of a second gets
        # one, also after the last, before the scene is taken out of it
        filters.append(f"[{inputs.count('-i') - 1}:v]fps={RATE},tpad=stop_mode=clone:stop_duration=30,"
                       f"trim=start={note['begins']}:duration={lasts},setpts=PTS-STARTPTS,"
                       f"scale={stage[0]}:{stage[1]}:flags=lanczos,fps={RATE},setsar=1,format=yuv420p[film{index}]")
        if len(words) != len(note["words"]) + 1:
            sys.exit(f"{file} has {len(words)} times two lines and says {len(note['words'])} times when the next come")
        begins = 0.0 if joined is None else at - FADE
        if joined is None:
            joined = f"film{index}"
        else:
            filters.append(f"[{joined}][film{index}]xfade=transition=fade:duration={FADE}:offset={begins:.3f}[joined{index}]")
            joined = f"joined{index}"
        said += zip([begins] + [begins + told / 1000 + LEAD for told in note["words"]], words, [f"{file}-words{each}" for each in range(len(words))])
        at = begins + lasts

    if system == "mac":
        sky_ = os.path.join(raw, "around.png")
        around(size, sky_)
        filters.append(f"[{joined}]pad={width}:{height}:{left}:{under}[placed]")
        filters.append(f"[placed][{still(sky_, at)}:v]overlay=shortest=1[under0]")
    else:
        filters.append(f"[{joined}]null[under0]")
    layers = 0
    for index, (comes, words, file) in enumerate(said):
        goes = said[index + 1][0] if index + 1 < len(said) else None
        for line in range(len(words)):
            drawn = os.path.join(raw, f"{file}-{line}.png")
            line_at(words, line, size, middle, top, letter, drawn)
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
        sys.exit(f"the preview would last {at:.1f} seconds, a preview lasts 15 to 30")
    film = os.path.join(out, f"gates-of-humanity-app-preview-{system}.mp4")
    rate = ("40M", "50M", "80M", "5.1") if system == "vision" else ("12M", "15M", "24M", "4.2")
    run("ffmpeg", "-v", "error", "-y", *inputs, "-f", "lavfi", "-t", f"{at:.3f}", "-i", "anullsrc=r=48000:cl=stereo",
        "-filter_complex", ";".join(filters), "-map", "[whole]", "-map", f"{inputs.count('-i')}:a",
        "-c:v", "libx264", "-profile:v", "high", "-level:v", rate[3], "-pix_fmt", "yuv420p", "-r", str(RATE),
        "-b:v", rate[0], "-maxrate", rate[1], "-bufsize", rate[2], "-c:a", "aac", "-b:a", "256k", "-movflags", "+faststart", film)
    print(f"{film}: {at:.1f} seconds")


def main():
    arguments = sys.argv[1:]
    if len(arguments) >= 3 and arguments[0] == "vision":
        capture_vision(arguments[1], arguments[2], arguments[3:])
    elif len(arguments) >= 2 and arguments[0] == "mac":
        capture_mac(arguments[1], arguments[2:])
    elif len(arguments) == 4 and arguments[0] == "cut" and arguments[1] in SIZES:
        cut(arguments[1], arguments[2], arguments[3].rstrip("/"))
    else:
        sys.exit(__doc__)


if __name__ == "__main__":
    main()
