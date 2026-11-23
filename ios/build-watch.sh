#!/usr/bin/env bash
# Builds the watch app for the simulator, with swiftc as ios/build.sh builds the iPhone app, and
# installs and starts it on the watch that's paired with a simulator of an iPhone. The watch
# app shows what the iPhone app sends it, and the iPhone only sends to a watch app that it has
# inside it, so ios/build.sh puts this one there and comes first, and both simulators run.
#
#   ios/build-watch.sh [simulator name of the iPhone]     "iPhone 17" by default
#   ios/build-watch.sh built                              only builds it, into ios/build/sim/Gates.app
set -euo pipefail

device=${1:-iPhone 17}
cd "$(dirname "$0")/.."

sdk=$(xcrun --sdk watchsimulator --show-sdk-path)
triple=arm64-apple-watchos10.0-simulator
# SwiftDraw draws what the site describes in SVG, as for the widgets: the version of project.yml
. ios/swiftdraw.sh
(cd "$swiftdraw" && swift build -c release --product SwiftDraw --scratch-path .build-watch --triple "$triple" --sdk "$sdk" > /dev/null)
drawing=(-I "$swiftdraw/.build-watch/release" -L "$swiftdraw/.build-watch/release" -lSwiftDraw)
# the folder that the watch app shares with what it has on a watch face
shared=(-Xlinker -sectcreate -Xlinker __TEXT -Xlinker __entitlements -Xlinker ios/widgets/Widgets.entitlements)
both=(ios/watch/Drawings.swift ios/widgets/Drawn.swift ios/widgets/Entries.swift)

app=ios/build/sim/Gates.app
rm -rf "$app"
complications="$app/PlugIns/Complications.appex"
mkdir -p "$complications"
SDKROOT="$sdk" xcrun --sdk watchsimulator swiftc -target "$triple" -sdk "$sdk" -module-name Gates -parse-as-library -O "${drawing[@]}" "${shared[@]}" \
    ios/watch/Gates.swift ios/watch/Received.swift ios/watch/Emblem.swift "${both[@]}" -o "$app/Gates"
SDKROOT="$sdk" xcrun --sdk watchsimulator swiftc -target "$triple" -sdk "$sdk" -module-name Complications -parse-as-library -O \
    -application-extension -Xlinker -e -Xlinker _NSExtensionMain "${drawing[@]}" "${shared[@]}" \
    ios/watch/Complications.swift "${both[@]}" -o "$complications/Complications"
cp ios/watch/Info.plist "$app/Info.plist"
cp ios/watch/Complications.plist "$complications/Info.plist"
codesign -f -s - --entitlements ios/widgets/Widgets.entitlements "$complications"
codesign -f -s - --entitlements ios/widgets/Widgets.entitlements "$app"

if [ "$device" = built ]; then
    exit
fi

. ios/watch/simulators.sh
xcrun simctl bootstatus "$phone" -b
xcrun simctl bootstatus "$watch" -b
xcrun simctl install "$watch" "$app"
xcrun simctl launch --terminate-running-process "$watch" com.uwyn.gatesofhumanity.watchkitapp
echo "Launched on the watch of $device, shut both down with: xcrun simctl shutdown $watch; xcrun simctl shutdown $phone"
