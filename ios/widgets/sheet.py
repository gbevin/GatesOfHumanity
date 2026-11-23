#!/usr/bin/env python3
"""Draws the widgets on one sheet, as the app does: asks the site on the JVM for the drawing of
every size at some moments and draws each with SwiftDraw, the library that the app draws them
with. For a look at a layout without building the app.

    ios/widgets/sheet.py OUT.png [yyyyMMddHHmm ...]   moments in UTC, two with short and long names by default

Needs Java, Swift and Pillow. Compiles the site, and builds SwiftDraw's command line tool, the
version that project.yml names, the first time into ios/build."""
import os
import re
import subprocess
import sys
import tempfile

from PIL import Image

ROOT = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "..")
WIDGETS = {"transits": ["systemSmall", "systemMedium", "accessoryRectangular", "accessoryCircular", "accessoryInline"],
           "bodygraph": ["systemSmall", "systemMedium", "systemLarge"],
           # someone who doesn't exist, born in Brussels on the fourteenth of March 1988 at 7:25
           "person/198803140625": ["systemSmall", "systemMedium", "systemLarge"]}
NAMED = "?name=Nora"
# the Sun in gate 43, Insight, and in gate 7, the Role of the Self in Interaction
MOMENTS = ["202611151700", "202608080000"]
SCALE, GAP, BEHIND = 2, 20, (90, 92, 128)


def run(command, cwd=ROOT, **options):
    return subprocess.run(command, check=True, cwd=cwd, **options)


def tool():
    version = re.search(r"^  SwiftDraw:.*?exactVersion: *(\S+)", open(os.path.join(ROOT, "ios", "project.yml")).read(), re.M | re.S).group(1)
    sources = os.path.join("ios", "build", "SwiftDraw-" + version)
    if not os.path.isdir(os.path.join(ROOT, sources)):
        run(["git", "clone", "--depth", "1", "--branch", version, "-q", "https://github.com/swhitty/SwiftDraw.git", sources])
    # apart from what ios/build.sh builds there for the simulator
    scratch = os.path.join(ROOT, sources, ".build-mac")
    built = subprocess.run(["swift", "build", "-c", "release", "--product", "swiftdrawcli", "--scratch-path", scratch],
                           cwd=os.path.join(ROOT, sources), capture_output=True, text=True)
    if built.returncode != 0:
        sys.exit(built.stdout + built.stderr)
    return os.path.join(scratch, "release", "swiftdrawcli")


def drawings(moments, out):
    run(["./bld", "compile"], stdout=subprocess.DEVNULL)
    jars = [os.path.join(folder, name) for folder in ("lib/compile", "lib/standalone") for name in sorted(os.listdir(os.path.join(ROOT, folder))) if name.endswith(".jar")]
    paths = [f"/widget/{widget}/{family}/{moment}" + (NAMED if widget.startswith("person") else "") for moment in moments for widget, families in WIDGETS.items() for family in families]
    # RIFE2 answers each address as the app's library does, and keeps every answer as a file
    run(["java", "-Drife.nativeapp.saved=" + out, "-cp", os.pathsep.join(["build/main", "src/main/resources"] + jars), "rife.nativeapp.NativeApp"] + paths, stdout=subprocess.DEVNULL)
    return {path: os.path.join(out, path[1:].replace("/", "_")) for path in paths}


def main():
    if len(sys.argv) < 2:
        sys.exit(__doc__)
    moments = sys.argv[2:] or MOMENTS
    draw = tool()
    with tempfile.TemporaryDirectory() as out:
        rows = []
        for moment in moments:
            row = []
            for path, svg in drawings([moment], out).items():
                os.rename(svg, svg + ".svg")
                run([draw, svg + ".svg", "--format", "png", "--scale", f"{SCALE}x", "--output", svg + ".png"], stdout=subprocess.DEVNULL)
                row.append(Image.open(svg + ".png").convert("RGBA"))
            rows.append(row)
        width = max(sum(image.width + GAP for image in row) for row in rows) + GAP
        height = sum(max(image.height for image in row) + GAP for row in rows) + GAP
        sheet = Image.new("RGB", (width, height), BEHIND)
        y = GAP
        for row in rows:
            x = GAP
            for image in row:
                sheet.paste(image, (x, y), image)
                x += image.width + GAP
            y += max(image.height for image in row) + GAP
        sheet.save(sys.argv[1])
    print(f"{sum(len(row) for row in rows)} drawings on {sys.argv[1]}")


if __name__ == "__main__":
    main()
