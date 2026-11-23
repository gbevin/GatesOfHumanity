#!/usr/bin/env python3
"""Draws the icon of the Android app from the drawing of ios/icon.py. Android cuts an icon to a shape of its own choosing out of two layers that are
half again as large as what shows: the sky is the one behind, and the emblem, small enough to
stay inside any shape, the one in front.

    android/icon.py

Needs Chrome and Pillow, as ios/icon.py does."""
import os
import subprocess
import sys
import tempfile

from PIL import Image

HERE = os.path.dirname(os.path.abspath(__file__))
sys.dont_write_bytecode = True
sys.path.insert(0, os.path.join(HERE, "..", "ios"))
import icon

RES = os.path.join(HERE, "app", "res")
# a layer is 108 points, of which a shape shows 72 at most and 66 for certain, at four pixels a point
LAYER = 432
# the outer ring of the emblem across the layer, which keeps it inside those 66 points
ACROSS = 0.6


def rendered(drawing, size, file):
    with tempfile.TemporaryDirectory() as work:
        open(os.path.join(work, "drawing.svg"), "w").write(drawing)
        open(os.path.join(work, "drawing.html"), "w").write(
            f'<!DOCTYPE html><html><body style="margin:0;background:transparent"><img src="drawing.svg" width="{size}" height="{size}" style="display:block"></body></html>')
        shot = os.path.join(work, "drawing.png")
        subprocess.run([icon.CHROME, "--headless=new", "--disable-gpu", "--hide-scrollbars", "--allow-file-access-from-files",
                        "--default-background-color=00000000", "--force-device-scale-factor=1", f"--window-size={size},{size}",
                        f"--screenshot={shot}", "file://" + os.path.join(work, "drawing.html")], check=True, capture_output=True)
        os.makedirs(os.path.dirname(file), exist_ok=True)
        Image.open(shot).save(file)
    print(f"Drew {os.path.relpath(file)}")


def main():
    whole = icon.svg()
    sky = '<rect width="1024" height="1024" fill="url(#sky)"/>'
    if sky not in whole or '<g clip-path="url(#shape)">' not in whole:
        sys.exit("ios/icon.py draws its icon in another way than this script takes apart")
    rendered(f'<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 1024 1024">{icon.DEFS}{sky}</svg>', LAYER,
             os.path.join(RES, "mipmap-xxxhdpi", "ic_launcher_background.png"))
    # the ring has a radius of 436 around 512, 512
    side = 2 * 436 / ACROSS
    front = whole.replace(sky, "").replace('<g clip-path="url(#shape)">', "<g>")
    front = front.replace('viewBox="0 0 1024 1024" width="1024" height="1024"', f'viewBox="{512 - side / 2} {512 - side / 2} {side} {side}"')
    rendered(front, LAYER, os.path.join(RES, "mipmap-xxxhdpi", "ic_launcher_foreground.png"))


if __name__ == "__main__":
    main()
