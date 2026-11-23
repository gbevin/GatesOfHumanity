#!/usr/bin/env bash
# Builds Gates of Humanity as an iOS app for the simulator: the site as a native library, which
# ./bld app-library ios-sim builds, inside a SwiftUI app whose web view asks the library for
# every page instead of a server, with the Swift package of RIFE2 that runs the site there.
# Needs the GraalVM that RIFE2's build extension names and Xcode.
#
#   ios/build.sh [simulator name]         builds, installs and launches, on "iPhone 17" by default
#   ios/build.sh app [simulator name]     the app alone, around the library that was built last
set -euo pipefail

cd "$(dirname "$0")/.."
library=native/ios-sim
if [ "${1:-}" = app ] && [ -d "$library/gatesofhumanity.framework" ]; then
    shift
else
    [ "${1:-}" = app ] && shift
    ./bld app-library ios-sim
fi
device=${1:-iPhone 17}
. "${RIFE2_NATIVE_APP:-../rife2/rife2-native-app}/apple/native-app.sh"
native_app arm64-apple-ios17.0-simulator iphonesimulator ios/build/sim/rife2-native-app

sdk=$(xcrun --sdk iphonesimulator --show-sdk-path)
# SwiftDraw draws the widgets, which the site describes in SVG: the version that project.yml names
# for the device build, built from its sources for the simulator
swiftdraw_version=$(sed -n '/^  SwiftDraw:/,/exactVersion/s/.*exactVersion: *//p' ios/project.yml)
swiftdraw="ios/build/SwiftDraw-$swiftdraw_version"
if [ ! -d "$swiftdraw" ]; then
    git clone --depth 1 --branch "$swiftdraw_version" -q https://github.com/swhitty/SwiftDraw.git "$swiftdraw"
fi
(cd "$swiftdraw" && swift build -c release --product SwiftDraw --triple arm64-apple-ios17.0-simulator --sdk "$sdk" > /dev/null)
drawing=(-I "$swiftdraw/.build/release" -L "$swiftdraw/.build/release" -lSwiftDraw)
# The folder that the app shares with its widgets: a simulator reads what a program may do from
# inside it. What the app may do with iCloud is for a device, a simulator has no account for it.
shared=(-Xlinker -sectcreate -Xlinker __TEXT -Xlinker __entitlements -Xlinker ios/widgets/Widgets.entitlements)
app=ios/build/sim/GatesOfHumanity.app
rm -rf "$app"
mkdir -p "$app/Frameworks"
SDKROOT="$sdk" xcrun --sdk iphonesimulator swiftc -target arm64-apple-ios17.0-simulator -sdk "$sdk" -module-name GatesOfHumanity -parse-as-library -O \
    "${native_app[@]}" -F "$library" -framework gatesofhumanity -Xlinker -rpath -Xlinker @executable_path/Frameworks \
    "${shared[@]}" ios/app/*.swift ios/watch/Drawings.swift ios/widgets/Drawn.swift -o "$app/GatesOfHumanity"
# the widgets are an extension inside the app, which iOS starts at the function that all extensions start at
widgets="$app/PlugIns/Widgets.appex"
mkdir -p "$widgets"
# A widget that asks whose it is only gets an answer when iOS knows the question, from a file that
# Xcode makes beside the extension out of what the compiler tells of its types. Without it the
# widget of a person stays empty in a simulator: "Intent configuration is required".
intents=ios/build/sim/intents
mkdir -p "$intents"
cp ios/widgets/protocols.json "$intents/protocols.json"
sources=(ios/widgets/*.swift ios/app/Site.swift ios/app/Saved.swift)
SDKROOT="$sdk" xcrun --sdk iphonesimulator swiftc -target arm64-apple-ios17.0-simulator -sdk "$sdk" -module-name Widgets -parse-as-library -O \
    -application-extension -Xlinker -e -Xlinker _NSExtensionMain -Xlinker -dependency_info -Xlinker "$intents/dependency.dat" \
    -whole-module-optimization -emit-const-values-path "$intents/Widgets.swiftconstvalues" \
    -Xfrontend -const-gather-protocols-file -Xfrontend "$intents/protocols.json" \
    "${native_app[@]}" -F "$library" -framework gatesofhumanity -Xlinker -rpath -Xlinker @executable_path/../../Frameworks \
    "${drawing[@]}" "${shared[@]}" \
    "${sources[@]}" -o "$widgets/Widgets"
printf '%s\n' "${sources[@]/#/$PWD/}" > "$intents/sources.txt"
echo "$PWD/$intents/Widgets.swiftconstvalues" > "$intents/constvalues.txt"
xcrun appintentsmetadataprocessor --toolchain-dir "$(dirname "$(dirname "$(dirname "$(xcrun -f swiftc)")")")" --module-name Widgets \
    --sdk-root "$sdk" --xcode-version "$(xcodebuild -version | sed -n 's/Build version //p')" --platform-family iOS --deployment-target 17.0 \
    --bundle-identifier com.uwyn.gatesofhumanity.widgets --output "$widgets" --target-triple arm64-apple-ios17.0-simulator \
    --binary-file "$widgets/Widgets" --dependency-file "$intents/dependency.dat" --stringsdata-file "$intents/shortcuts.stringsdata" \
    --source-file-list "$intents/sources.txt" --swift-const-vals-list "$intents/constvalues.txt" \
    --compile-time-extraction --deployment-aware-processing --no-app-shortcuts-localization > "$intents/metadata.log" 2>&1 ||
    { echo "The App Intents metadata of the widgets wasn't made, see $intents/metadata.log" >&2; exit 1; }
cp ios/widgets/Info.plist "$widgets/Info.plist"
# the watch app is inside the app of the iPhone, which only sends to a watch app that it has
ios/build-watch.sh built
mkdir -p "$app/Watch"
cp -R ios/build/sim/Gates.app "$app/Watch/"
cp -R "$library/gatesofhumanity.framework" "$app/Frameworks/"
cp ios/app/PrivacyInfo.xcprivacy "$app/"
cp ios/app/Info.plist "$app/Info.plist"
codesign -f -s - "$app/Frameworks/gatesofhumanity.framework"
codesign -f -s - --entitlements ios/widgets/Widgets.entitlements "$widgets"
codesign -f -s - --entitlements ios/widgets/Widgets.entitlements "$app"

udid=$(./bld app-simulator ios-sim "$device")
xcrun simctl bootstatus "$udid" -b
xcrun simctl install "$udid" "$app"
xcrun simctl launch --terminate-running-process "$udid" com.uwyn.gatesofhumanity
echo "Launched on $device, shut it down with: xcrun simctl shutdown $udid"
