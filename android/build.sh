#!/usr/bin/env bash
# Builds Gates of Humanity as an Android app: the site as a native library, which ./bld app-library android
# builds, inside an app whose web view asks the library for every page instead of a server,
# which ./bld app-android puts together with the classes of RIFE2 that run the site there, as
# AppBuild.java describes it. It's installed and started in the emulator or the device that adb
# sees, when there's one.
#
#   android/build.sh         the library and the app
#   android/build.sh app     the app alone, around the library that was built last
#   android/build.sh bundle  the library and the app bundle for Google Play, android/build/Gates.aab
#
# With GATES_PROBED set, the app is one whose pages a program on this Mac can look into, for
# android/check-app.sh: android/build/Gates-probed.apk, which isn't one to hand to anyone.
set -euo pipefail

cd "$(dirname "$0")/.."
# AndroidSVG draws the widgets, which the site describes in SVG
svg_version=1.4
svg=android/build/lib/androidsvg-$svg_version.jar
if [ ! -f "$svg" ]; then
    mkdir -p android/build/lib
    # under its name only when it's the one that this build is for, so that half of it isn't taken for it later
    curl -sSfL -o "$svg.fetched" "https://repo1.maven.org/maven2/com/caverock/androidsvg/$svg_version/androidsvg-$svg_version.jar"
    echo "729c67ef475f19f112d01e4fc8e55b1f23841912f8e5090db27992801a04f79d  $svg.fetched" | shasum -a 256 -c - > /dev/null ||
        { echo "What Maven Central has as AndroidSVG $svg_version isn't the one that this build is for" >&2; rm -f "$svg.fetched"; exit 1; }
    mv "$svg.fetched" "$svg"
fi
if [ "${1:-}" != app ] || [ ! -f native/android/libgatesofhumanity.so ]; then
    ./bld app-library android
fi
if [ "${1:-}" = bundle ]; then
    ./bld app-android-bundle
else
    ./bld app-android
fi
