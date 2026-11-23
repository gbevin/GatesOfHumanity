#!/usr/bin/env python3
"""Draws the icon of the app, the wheel of the 64 gates with a few of them lit around the
outline of the chart's body graph, and renders it into the asset catalog with headless Chrome.
The site has the same icon: as a drawing with rounded corners for the tab of a browser, as a
picture for the home screen of a phone, and as the pictures that a browser installs the site with. The emblem alone, without the sky around it, becomes the
drawing that the site welcomes with. visionOS has the icon in three layers, one before the
other: the sky, the wheel, and the outline of the body graph in its middle.

    ios/icon.py

Needs Chrome and Pillow. CHROME names another Chrome or Chromium binary."""

import json
import math
import os
import subprocess
import tempfile

from PIL import Image

ROOT = os.path.dirname(os.path.abspath(__file__))
ICON = os.path.join(ROOT, "app", "Assets.xcassets", "AppIcon.appiconset", "icon1024.png")
IMAGES = os.path.join(ROOT, "..", "src", "main", "webapp", "images")
EMBLEM = os.path.join(IMAGES, "emblem.svg")
FAVICON = os.path.join(IMAGES, "favicon.svg")
TOUCH_ICON = os.path.join(IMAGES, "apple-touch-icon.png")
TOUCH_SIZE = 180
# what site.webmanifest names
INSTALLED_SIZES = (192, 512)
MASKABLE = os.path.join(IMAGES, "icon-maskable-512.png")
# Android cuts a maskable icon to a shape of its own, of which only the circle of 0.8 across is sure to stay
MASKABLE_SCALE = 0.9
LAYERED = os.path.join(ROOT, "..", "vision", "app", "Assets.xcassets", "AppIcon.solidimagestack")
CHROME = os.environ.get("CHROME", "/Applications/Google Chrome.app/Contents/MacOS/Google Chrome")

SIZE = 1024
CENTER = SIZE / 2
GATES = 64
# the gates that are lit, spread the way the planets of a chart are: a few alone and some side by side
LIT = {1, 8, 9, 20, 29, 36, 44, 45, 57}

EMBLEM_DEFS = """<radialGradient id="night" cx="50%" cy="42%" r="60%"><stop offset="0" stop-color="#232360"/><stop offset="1" stop-color="#0d0d2a"/></radialGradient>
<linearGradient id="gold" x1="0" y1="0" x2="0" y2="1"><stop offset="0" stop-color="#fbd67e"/><stop offset="1" stop-color="#d8922a"/></linearGradient>
<radialGradient id="ember" cx="50%" cy="56%" r="50%"><stop offset="0" stop-color="#f3bd58" stop-opacity="0.32"/><stop offset="1" stop-color="#f3bd58" stop-opacity="0"/></radialGradient>"""
DEFS = f"""<defs>
<radialGradient id="sky" cx="50%" cy="38%" r="78%"><stop offset="0" stop-color="#3b3b8a"/><stop offset="0.5" stop-color="#1f1f52"/><stop offset="1" stop-color="#0b0b24"/></radialGradient>
<radialGradient id="halo" cx="50%" cy="50%" r="50%"><stop offset="0.72" stop-color="#a6a6ee" stop-opacity="0.42"/><stop offset="1" stop-color="#a6a6ee" stop-opacity="0"/></radialGradient>
<filter id="soft" x="-20%" y="-20%" width="140%" height="140%"><feDropShadow dx="0" dy="8" stdDeviation="10" flood-color="#04041a" flood-opacity="0.55"/></filter>
{EMBLEM_DEFS}
</defs>"""


def point(radius, degrees):
    angle = math.radians(degrees - 90)
    return CENTER + radius * math.cos(angle), CENTER + radius * math.sin(angle)


def gate(inner, outer, start, end):
    x1, y1 = point(outer, start)
    x2, y2 = point(outer, end)
    x3, y3 = point(inner, end)
    x4, y4 = point(inner, start)
    return f"M{x1:.2f} {y1:.2f}A{outer} {outer} 0 0 1 {x2:.2f} {y2:.2f}L{x3:.2f} {y3:.2f}A{inner} {inner} 0 0 0 {x4:.2f} {y4:.2f}Z"


def wheel(inner, outer, gap=1.1):
    step = 360 / GATES
    return "\n".join(
        f'<path d="{gate(inner, outer, i * step + gap / 2, (i + 1) * step - gap / 2)}" '
        f'fill="{"url(#gold)" if i in LIT else ("#9292c6", "#8080b6")[i % 2]}"/>'
        for i in range(GATES))


def outline(radius):
    # the outline around the body graph in wheel.svg, M 480 130 L 205 675 A 340 340 0 0 0 755 675 Z:
    # its corners and its curved edge lie on a circle around 480, 472.5, which is the middle here,
    # so that they're all as close to the circle around it
    scale = radius / 342.5

    def at(x, y):
        return f"{CENTER + (x - 480) * scale:.1f} {CENTER + (y - 472.5) * scale:.1f}"

    return f"M{at(480, 130)}L{at(205, 675)}A{340 * scale:.1f} {340 * scale:.1f} 0 0 0 {at(755, 675)}Z"


def svg(corner=0, scale=1):
    # the emblem stays well inside the square, whose corners the system rounds for an app and
    # the drawing itself for a browser, which shows it as it is
    return f"""<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 {SIZE} {SIZE}" width="{SIZE}" height="{SIZE}">{DEFS}
<clipPath id="shape"><rect width="{SIZE}" height="{SIZE}" rx="{corner}"/></clipPath>
<g clip-path="url(#shape)">
<rect width="{SIZE}" height="{SIZE}" fill="url(#sky)"/>{"" if scale == 1 else f"""
<g transform="translate({CENTER} {CENTER}) scale({scale}) translate({-CENTER} {-CENTER})">"""}
<circle cx="512" cy="512" r="520" fill="url(#halo)"/>
<circle cx="512" cy="512" r="436" fill="none" stroke="#a4a4de" stroke-opacity="0.55" stroke-width="6"/>
<g filter="url(#soft)">{wheel(318, 416)}</g>
<circle cx="512" cy="512" r="300" fill="url(#night)" stroke="#a4a4de" stroke-opacity="0.6" stroke-width="6"/>
<circle cx="512" cy="512" r="300" fill="url(#ember)"/>
<path d="{outline(240)}" fill="url(#gold)" stroke="url(#gold)" stroke-width="32" stroke-linejoin="round"/>{"" if scale == 1 else """
</g>"""}
</g>
</svg>"""


def layers():
    # what the icon is made of, from behind to in front, each as large as the icon
    def layer(drawn):
        return f'''<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 {SIZE} {SIZE}" width="{SIZE}" height="{SIZE}">{DEFS}
{drawn}
</svg>'''

    return [
        ("Back", layer(f'''<rect width="{SIZE}" height="{SIZE}" fill="url(#sky)"/>
<circle cx="512" cy="512" r="520" fill="url(#halo)"/>''')),
        ("Middle", layer(f'''<circle cx="512" cy="512" r="436" fill="none" stroke="#a4a4de" stroke-opacity="0.55" stroke-width="6"/>
<g filter="url(#soft)">{wheel(318, 416)}</g>''')),
        ("Front", layer(f'''<circle cx="512" cy="512" r="300" fill="url(#night)" stroke="#a4a4de" stroke-opacity="0.6" stroke-width="6"/>
<circle cx="512" cy="512" r="300" fill="url(#ember)"/>
<path d="{outline(240)}" fill="url(#gold)" stroke="url(#gold)" stroke-width="32" stroke-linejoin="round"/>''')),
    ]


def render(work, name, drawing, see_through=False):
    open(os.path.join(work, name + ".svg"), "w").write(drawing)
    open(os.path.join(work, name + ".html"), "w").write(
        f'<!DOCTYPE html><html><body style="margin:0"><img src="{name}.svg" width="{SIZE}" height="{SIZE}" style="display:block"></body></html>')
    shot = os.path.join(work, name + ".png")
    subprocess.run([CHROME, "--headless=new", "--disable-gpu", "--hide-scrollbars", "--allow-file-access-from-files",
                    "--force-device-scale-factor=1", f"--window-size={SIZE},{SIZE}", f"--screenshot={shot}"] +
                   (["--default-background-color=00000000"] if see_through else []) +
                   ["file://" + os.path.join(work, name + ".html")], check=True, capture_output=True)
    return shot


def contents(folder, written):
    os.makedirs(folder, exist_ok=True)
    json.dump(written, open(os.path.join(folder, "Contents.json"), "w"), indent=2)
    open(os.path.join(folder, "Contents.json"), "a").write("\n")


def emblem():
    # as far as its outer ring, with nothing around it
    return f"""<svg xmlns="http://www.w3.org/2000/svg" viewBox="72 72 880 880"><defs>
{EMBLEM_DEFS}
</defs>
<circle cx="512" cy="512" r="436" fill="none" stroke="#a4a4de" stroke-opacity="0.55" stroke-width="6"/>
{wheel(318, 416)}
<circle cx="512" cy="512" r="300" fill="url(#night)" stroke="#a4a4de" stroke-opacity="0.6" stroke-width="6"/>
<circle cx="512" cy="512" r="300" fill="url(#ember)"/>
<path d="{outline(240)}" fill="url(#gold)" stroke="url(#gold)" stroke-width="32" stroke-linejoin="round"/>
</svg>
"""


def main():
    open(EMBLEM, "w").write(emblem())
    print(f"Drew {os.path.relpath(EMBLEM)}")
    # the corners of an app icon on iOS
    open(FAVICON, "w").write(svg(corner=230) + "\n")
    print(f"Drew {os.path.relpath(FAVICON)}")
    with tempfile.TemporaryDirectory() as work:
        # an app icon has no transparency
        Image.open(render(work, "icon", svg())).convert("RGB").save(ICON)
        print(f"Drew {os.path.relpath(ICON)}")
        # the layers of visionOS, of which only the one behind is all filled in
        info = {"author": "xcode", "version": 1}
        contents(os.path.dirname(LAYERED), {"info": info})
        drawn = layers()
        contents(LAYERED, {"info": info, "layers": [{"filename": name + ".solidimagestacklayer"} for name, _ in reversed(drawn)]})
        for name, drawing in drawn:
            layer = os.path.join(LAYERED, name + ".solidimagestacklayer")
            contents(layer, {"info": info})
            contents(os.path.join(layer, "Content.imageset"), {"images": [{"filename": name + ".png", "idiom": "vision", "scale": "2x"}], "info": info})
            shot = Image.open(render(work, name, drawing, see_through=name != "Back"))
            (shot.convert("RGB") if name == "Back" else shot).save(os.path.join(layer, "Content.imageset", name + ".png"))
        print(f"Drew {os.path.relpath(LAYERED)}")
        Image.open(render(work, "maskable", svg(scale=MASKABLE_SCALE))).convert("RGB").resize((512, 512), Image.LANCZOS).save(MASKABLE)
        print(f"Drew {os.path.relpath(MASKABLE)}")
    # a phone rounds the corners of this one itself
    Image.open(ICON).resize((TOUCH_SIZE, TOUCH_SIZE), Image.LANCZOS).save(TOUCH_ICON)
    print(f"Drew {os.path.relpath(TOUCH_ICON)}")
    for size in INSTALLED_SIZES:
        installed = os.path.join(IMAGES, f"icon-{size}.png")
        Image.open(ICON).resize((size, size), Image.LANCZOS).save(installed)
        print(f"Drew {os.path.relpath(installed)}")


if __name__ == "__main__":
    main()
