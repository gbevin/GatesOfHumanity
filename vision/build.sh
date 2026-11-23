#!/usr/bin/env bash
# Builds Gates of Humanity as an app for visionOS, for the simulator: the site as a native library, which
# ./bld app-library vision-sim builds, inside a SwiftUI app whose web view asks the library for
# every page instead of a server, with the Swift package of RIFE2 that runs the site there. It
# has the files of the iOS app that visionOS takes as they are, and its own for what a window
# in a room has around it. It's put together with swiftc, as ios/build.sh does for the
# simulator of an iPhone.
#
#   vision/build.sh [simulator name]     builds, installs and launches, on "Apple Vision Pro" by default
#   vision/build.sh app [simulator name] the app alone, around the library that was built last
set -euo pipefail

cd "$(dirname "$0")/.."
library=native/vision-sim
if [ "${1:-}" = app ] && [ -d "$library/gatesofhumanity.framework" ]; then
    shift
else
    [ "${1:-}" = app ] && shift
    ./bld app-library vision-sim
fi
. "${RIFE2_NATIVE_APP:-../rife2/rife2-native-app}/apple/native-app.sh"
native_app arm64-apple-xros26.0-simulator xrsimulator vision/build/sim/rife2-native-app
device=${1:-Apple Vision Pro}

sdk=$(xcrun --sdk xrsimulator --show-sdk-path)
triple=arm64-apple-xros26.0-simulator
# the folder that the app shares with its widgets, which a simulator reads from inside the program
shared=(-Xlinker -sectcreate -Xlinker __TEXT -Xlinker __entitlements -Xlinker ios/widgets/Widgets.entitlements)
app=vision/build/sim/Gates.app
rm -rf "$app"
mkdir -p "$app/Frameworks"
# the files of the iOS app that are the same for visionOS
same=(ios/app/Site.swift ios/app/Pages.swift ios/app/People.swift ios/app/Saved.swift ios/app/Elsewhere.swift
      ios/app/Menus.swift ios/app/Platform.swift ios/app/Probe.swift)
SDKROOT="$sdk" xcrun --sdk xrsimulator swiftc -target "$triple" -sdk "$sdk" -module-name Gates -parse-as-library -O \
    "${native_app[@]}" -F "$library" -framework gatesofhumanity -Xlinker -rpath -Xlinker @executable_path/Frameworks \
    "${shared[@]}" vision/app/*.swift "${same[@]}" -o "$app/Gates"
cp -R "$library/gatesofhumanity.framework" "$app/Frameworks/"
cp vision/app/PrivacyInfo.xcprivacy "$app/"
cp vision/app/Info.plist "$app/Info.plist"
codesign -f -s - "$app/Frameworks/gatesofhumanity.framework"
codesign -f -s - --entitlements ios/widgets/Widgets.entitlements "$app"

udid=$(./bld app-simulator vision-sim "$device")
xcrun simctl bootstatus "$udid" -b > /dev/null
xcrun simctl install "$udid" "$app"
xcrun simctl launch --terminate-running-process "$udid" com.uwyn.gatesofhumanity
echo "Launched on $device, shut it down with: xcrun simctl shutdown $udid"
