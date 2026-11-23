#!/usr/bin/env python3
"""Draws what Android shows of each widget where one is chosen: the drawing that the site makes
of it at a moment, as a picture among the resources of the app.

    android/previews.py

Needs what ios/widgets/sheet.py needs, whose drawings and tool these are."""
import os
import subprocess
import sys
import tempfile

ROOT = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..")
sys.dont_write_bytecode = True
sys.path.insert(0, os.path.join(ROOT, "ios", "widgets"))
import sheet  # noqa: E402

# the size that each widget has when it's placed
SHOWN = {"transits": "/widget/transits/systemSmall", "bodygraph": "/widget/bodygraph/systemMedium", "person": "/widget/person/198803140625/systemMedium"}


def main():
    draw = sheet.tool()
    with tempfile.TemporaryDirectory() as out:
        drawings = sheet.drawings(sheet.MOMENTS[:1], out)
        for name, path in SHOWN.items():
            svg = next(file for asked, file in drawings.items() if asked.startswith(path + "/"))
            os.rename(svg, svg + ".svg")
            picture = os.path.join(ROOT, "android", "app", "res", "drawable-nodpi", f"preview_{name}.png")
            subprocess.run([draw, svg + ".svg", "--format", "png", "--scale", "3x", "--output", picture], check=True, stdout=subprocess.DEVNULL)
            print("drew " + os.path.relpath(picture, ROOT))


if __name__ == "__main__":
    main()
