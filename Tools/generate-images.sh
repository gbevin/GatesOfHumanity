#!/usr/bin/env bash
# Renders the link preview image in src/main/webapp/images with headless Chrome, from a local
# server started with ./bld compile run. Set CHROME to another Chrome or Chromium binary.
#
#   Tools/generate-images.sh [port]
set -euo pipefail

port=${1:-8080}
chrome=${CHROME:-"/Applications/Google Chrome.app/Contents/MacOS/Google Chrome"}
cd "$(dirname "$0")/.."
root=$PWD
if ! curl -sf -o /dev/null "http://localhost:$port/wheel"; then
    echo "Start the server first with ./bld compile run" >&2
    exit 1
fi

work=$(mktemp -d)
trap 'rm -rf "$work"' EXIT

# a connection chart of two fixed moments, which lights every center and draws the channels in both
# people's colors, so the image only changes when the chart drawing does
wheel="http://localhost:$port/wheel/image/connection/199604050800/2643743/199807191300/2643743"
sed "s|WHEEL_URL|$wheel|" Tools/images/og-image.html > "$work/og-image.html"

render() {
    "$chrome" --headless=new --disable-gpu --hide-scrollbars --allow-file-access-from-files \
        --force-device-scale-factor=1 --window-size="$2" --virtual-time-budget=5000 \
        --screenshot="$root/$3/$1.png" "file://$work/$1.html" >/dev/null 2>&1
}
render og-image 1200,630 src/main/webapp/images
file src/main/webapp/images/og-image.png
