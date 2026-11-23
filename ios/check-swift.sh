#!/usr/bin/env bash
# Checks what the Swift of the app works out by itself, on this Mac and without the site: how a
# drawing of a widget is taken apart, how a name reaches the site, what the watch is sent, and of
# the chart in the room on visionOS how an outline becomes triangles, which shapes slide to
# where they are next, and what of a shape a tap lands on. The checks are tests/main.swift,
# compiled with the files that they check.
#
#   ios/check-swift.sh
set -euo pipefail

cd "$(dirname "$0")/.."
built=ios/build/checks
mkdir -p "$built"
swiftc -O -o "$built/checks" ios/tests/main.swift ios/widgets/Drawn.swift ios/app/Saved.swift ios/watch/Drawings.swift vision/app/Outline.swift vision/app/Moves.swift vision/app/Shape.swift
"$built/checks"
