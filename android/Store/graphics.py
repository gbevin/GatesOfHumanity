#!/usr/bin/env python3
"""Makes the two pictures that Google Play asks for beside the screenshots: the icon of 512 x 512,
from the icon of the iOS app that ios/icon.py draws, and the feature graphic of 1024 x 500, the
emblem of the site beside the name on the night sky of the icon.

    android/Store/graphics.py OUT

Needs Chrome and Pillow. CHROME names another Chrome or Chromium binary."""
import importlib.util
import os
import subprocess
import sys
import tempfile

from PIL import Image, ImageFilter

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.join(HERE, "..", "..")
spec = importlib.util.spec_from_file_location("ios_screenshots", os.path.join(ROOT, "ios", "Store", "screenshots.py"))
ios = importlib.util.module_from_spec(spec)
spec.loader.exec_module(ios)

ICON = os.path.join(ROOT, "ios", "app", "Assets.xcassets", "AppIcon.appiconset", "icon1024.png")
EMBLEM = os.path.join(ROOT, "src", "main", "webapp", "images", "emblem.svg")
CHROME = os.environ.get("CHROME", "/Applications/Google Chrome.app/Contents/MacOS/Google Chrome")
FEATURE = (1024, 500)


def emblem(size):
    with tempfile.TemporaryDirectory() as work:
        page = os.path.join(work, "emblem.html")
        open(page, "w").write(f'<!DOCTYPE html><html><body style="margin:0"><img src="file://{EMBLEM}" width="{size}" height="{size}" style="display:block"></body></html>')
        shot = os.path.join(work, "emblem.png")
        subprocess.run([CHROME, "--headless=new", "--disable-gpu", "--hide-scrollbars", "--allow-file-access-from-files",
                        "--force-device-scale-factor=1", f"--window-size={size},{size}", "--default-background-color=00000000",
                        f"--screenshot={shot}", "file://" + page], check=True, capture_output=True)
        return Image.open(shot).convert("RGBA")


def feature(file):
    width, height = FEATURE
    canvas = ios.sky(FEATURE).convert("RGBA")
    drawn = emblem(400)
    # the glow of the Facebook image, around the emblem
    glow = Image.new("RGBA", FEATURE, (227, 168, 60, 0))
    disc = Image.new("L", FEATURE, 0)
    disc.paste(drawn.getchannel("A"), (60, (height - drawn.height) // 2))
    glow.putalpha(disc.filter(ImageFilter.GaussianBlur(40)).point(lambda value: round(value * 0.35)))
    canvas.alpha_composite(glow)
    canvas.alpha_composite(drawn, (60, (height - drawn.height) // 2))

    # the name in gold, and under it what the app is, in the middle of the room right of the emblem
    room = (520, width - 40)
    lines = [(ios.lettering("GATES OF", 58, 58 * 0.06), True), (ios.lettering("HUMANITY", 58, 58 * 0.06), True),
             (ios.lettering("HUMAN DESIGN CHARTS", 26, 26 * 0.12), False)]
    spacing = [72, 92, 0]
    total = sum(spacing) + lines[-1][0].height
    y = (height - total) // 2
    for (line, golden), step in zip(lines, spacing):
        if line.width > room[1] - room[0]:
            sys.exit("a line of the feature graphic is wider than its room")
        shadow = Image.new("RGBA", line.size, (6, 6, 26, 0))
        shadow.putalpha(line.filter(ImageFilter.GaussianBlur(6)).point(lambda value: round(value * 0.7)))
        at = (room[0] + (room[1] - room[0] - line.width) // 2, y)
        canvas.alpha_composite(shadow, (at[0], at[1] + 3))
        color = ios.gold(line) if golden else Image.new("RGB", line.size, ios.WHITE)
        canvas.paste(color, at, line)
        y += step
    canvas.convert("RGB").save(file)


def main():
    if len(sys.argv) != 2:
        sys.exit(__doc__)
    out = sys.argv[1]
    os.makedirs(out, exist_ok=True)
    Image.open(ICON).convert("RGBA").resize((512, 512), Image.LANCZOS).save(os.path.join(out, "icon-512.png"))
    feature(os.path.join(out, "feature-graphic.png"))
    print(f"icon-512.png and feature-graphic.png written to {out}")


if __name__ == "__main__":
    main()
