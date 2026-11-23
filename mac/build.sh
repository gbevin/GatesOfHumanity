#!/usr/bin/env bash
# Builds Gates of Humanity as an app for a Mac: the site as a native library, which ./bld app-library mac
# builds, inside a SwiftUI app whose web view asks the library for every page instead of a
# server, with the Swift package of RIFE2 that runs the site there. It
# has the files of the iOS app that don't depend on iOS, and its own for what a Mac does
# otherwise. It's put together with swiftc, as ios/build.sh does for the simulator.
#
#   mac/build.sh         the library and the app, into mac/build/Gates.app
#   mac/build.sh app     the app alone, around the library that was built last
#
# With GATES_PROBED set, the app is one that the checks can look into, see mac/app/Probe.swift.
# It's another app to the Mac than the one that someone uses, under a name of its own, so that
# what a check or a capture saves in it leaves the people of that one alone.
# With GATES_RELEASED set, it's signed for other Macs than this one, with the Developer ID
# certificate of the team, as a published build is.
# iCloud, which keeps the saved people the same on someone's devices, only lets an app in that
# carries a provisioning profile of the team for it, see mac/profile.py. A released app has to
# have one. An app for this Mac alone is built without when there's none, and keeps its people
# on this Mac, as the one for the checks always does.
set -euo pipefail

cd "$(dirname "$0")/.."
library=native/mac
if [ "${1:-}" != app ] || [ ! -d "$library/gatesofhumanity.framework" ]; then
    ./bld app-library mac
fi
. "${RIFE2_NATIVE_APP:-../rife2/rife2-native-app}/apple/native-app.sh"
native_app arm64-apple-macos14.0 "" mac/build/rife2-native-app

app=mac/build/Gates.app
identifier=com.uwyn.gatesofhumanity${GATES_PROBED:+.probed}
rm -rf "$app"
mkdir -p "$app/Contents/MacOS" "$app/Contents/Frameworks" "$app/Contents/Resources"
# the files of the iOS app that are the same for a Mac
shared=(ios/app/Site.swift ios/app/Pages.swift ios/app/People.swift ios/app/Saved.swift ios/app/Elsewhere.swift ios/app/Menus.swift)
xcrun swiftc -target arm64-apple-macos14.0 -module-name Gates -parse-as-library -O ${GATES_PROBED:+-D PROBED} \
    "${native_app[@]}" -F "$library" -framework gatesofhumanity -Xlinker -rpath -Xlinker @executable_path/../Frameworks \
    mac/app/*.swift "${shared[@]}" -o "$app/Contents/MacOS/Gates"
cp -R "$library/gatesofhumanity.framework" "$app/Contents/Frameworks/"

# The widgets are an extension inside the app, the ones of the iOS app with SwiftDraw as it's
# built for a Mac: the version that ios/project.yml names.
. ios/swiftdraw.sh
(cd "$swiftdraw" && swift build -c release --product SwiftDraw --scratch-path .build-mac > /dev/null)
widgets="$app/Contents/PlugIns/Widgets.appex"
mkdir -p "$widgets/Contents/MacOS" "$widgets/Contents/Resources"
# The widgets are linked to the library of the app, which is loaded with them at their start:
# an extension of a Mac is closed in once it runs, and may not open a file of its app anymore.
# A widget that asks whose it is only gets an answer when the Mac knows the question, from a
# file that Xcode makes beside the extension out of what the compiler tells of its types.
intents=mac/build/intents
rm -rf "$intents"
mkdir -p "$intents"
cp ios/widgets/protocols.json "$intents/protocols.json"
sources=(ios/widgets/*.swift ios/app/Site.swift ios/app/Saved.swift)
xcrun swiftc -target arm64-apple-macos14.0 -module-name Widgets -parse-as-library -O \
    -application-extension -Xlinker -e -Xlinker _NSExtensionMain -Xlinker -dependency_info -Xlinker "$intents/dependency.dat" \
    -whole-module-optimization -emit-const-values-path "$intents/Widgets.swiftconstvalues" \
    -Xfrontend -const-gather-protocols-file -Xfrontend "$intents/protocols.json" \
    "${native_app[@]}" -I "$swiftdraw/.build-mac/release" -L "$swiftdraw/.build-mac/release" -lSwiftDraw \
    -F "$library" -framework gatesofhumanity -Xlinker -rpath -Xlinker @loader_path/../../../../Frameworks \
    "${sources[@]}" -o "$widgets/Contents/MacOS/Widgets"
printf '%s\n' "${sources[@]/#/$PWD/}" > "$intents/sources.txt"
echo "$PWD/$intents/Widgets.swiftconstvalues" > "$intents/constvalues.txt"
xcrun appintentsmetadataprocessor --toolchain-dir "$(dirname "$(dirname "$(dirname "$(xcrun -f swiftc)")")")" --module-name Widgets \
    --sdk-root "$(xcrun --sdk macosx --show-sdk-path)" --xcode-version "$(xcodebuild -version | sed -n 's/Build version //p')" --platform-family macOS --deployment-target 14.0 \
    --bundle-identifier $identifier.widgets --output "$widgets/Contents/Resources" --target-triple arm64-apple-macos14.0 \
    --binary-file "$widgets/Contents/MacOS/Widgets" --dependency-file "$intents/dependency.dat" --stringsdata-file "$intents/shortcuts.stringsdata" \
    --source-file-list "$intents/sources.txt" --swift-const-vals-list "$intents/constvalues.txt" \
    --compile-time-extraction --deployment-aware-processing --no-app-shortcuts-localization > "$intents/metadata.log" 2>&1 ||
    { echo "The App Intents metadata of the widgets wasn't made, see $intents/metadata.log" >&2; exit 1; }

# the icon of the iOS app, in the sizes that a Mac asks for
icons=mac/build/Gates.iconset
rm -rf "$icons"
mkdir -p "$icons"
for size in 16 32 128 256 512; do
    sips -z $size $size ios/app/Assets.xcassets/AppIcon.appiconset/icon1024.png --out "$icons/icon_${size}x${size}.png" > /dev/null
    sips -z $((size * 2)) $((size * 2)) ios/app/Assets.xcassets/AppIcon.appiconset/icon1024.png --out "$icons/icon_${size}x${size}@2x.png" > /dev/null
done
iconutil -c icns -o "$app/Contents/Resources/Gates.icns" "$icons"
cp ios/app/PrivacyInfo.xcprivacy "$app/Contents/Resources/"

# the version, the build number and the team are those of the iOS app
version=$(sed -n 's/.*MARKETING_VERSION: "\(.*\)"/\1/p' ios/project.yml)
build=$(sed -n 's/.*CURRENT_PROJECT_VERSION: \(.*\)/\1/p' ios/project.yml)
team=$(sed -n 's/.*DEVELOPMENT_TEAM: *//p' ios/project.yml)
cat > "$app/Contents/Info.plist" <<PLIST
<?xml version="1.0" encoding="UTF-8"?>
<!DOCTYPE plist PUBLIC "-//Apple//DTD PLIST 1.0//EN" "http://www.apple.com/DTDs/PropertyList-1.0.dtd">
<plist version="1.0">
<dict>
    <key>CFBundleIdentifier</key><string>$identifier</string>
    <key>CFBundleExecutable</key><string>Gates</string>
    <key>CFBundleName</key><string>Gates of Humanity</string>
    <key>CFBundleDisplayName</key><string>Gates of Humanity</string>
    <key>CFBundleIconFile</key><string>Gates</string>
    <key>CFBundlePackageType</key><string>APPL</string>
    <key>CFBundleVersion</key><string>$build</string>
    <key>CFBundleShortVersionString</key><string>$version</string>
    <key>CFBundleInfoDictionaryVersion</key><string>6.0</string>
    <key>LSMinimumSystemVersion</key><string>14.0</string>
    <key>LSApplicationCategoryType</key><string>public.app-category.lifestyle</string>
    <key>NSHighResolutionCapable</key><true/>
    <key>NSPrincipalClass</key><string>NSApplication</string>
    <key>AppGroup</key><string>$team.$identifier</string>
</dict>
</plist>
PLIST
cat > "$widgets/Contents/Info.plist" <<PLIST
<?xml version="1.0" encoding="UTF-8"?>
<!DOCTYPE plist PUBLIC "-//Apple//DTD PLIST 1.0//EN" "http://www.apple.com/DTDs/PropertyList-1.0.dtd">
<plist version="1.0">
<dict>
    <key>CFBundleIdentifier</key><string>$identifier.widgets</string>
    <key>CFBundleExecutable</key><string>Widgets</string>
    <key>CFBundleName</key><string>Widgets</string>
    <key>CFBundleDisplayName</key><string>Gates of Humanity</string>
    <key>CFBundlePackageType</key><string>XPC!</string>
    <key>CFBundleVersion</key><string>$build</string>
    <key>CFBundleShortVersionString</key><string>$version</string>
    <key>CFBundleInfoDictionaryVersion</key><string>6.0</string>
    <key>LSMinimumSystemVersion</key><string>14.0</string>
    <key>AppGroup</key><string>$team.$identifier</string>
    <key>NSExtension</key>
    <dict>
        <key>NSExtensionPointIdentifier</key><string>com.apple.widgetkit-extension</string>
    </dict>
</dict>
</plist>
PLIST
# Signed as the Mac App Store asks: closed in, with only what it says that it does, and without
# the leave to run code that wasn't signed, which the library doesn't need since all of it was
# compiled beforehand. That takes someone to sign it, the development certificate of the team
# on this Mac: an app that nobody signed isn't let load a library, signed by nobody either,
# under those rules, nor share a folder with its widgets, which is named after the team.
# Another Mac only opens what the team's Developer ID certificate signed, at a time that Apple
# vouches for.
certificate=${GATES_RELEASED:+Developer ID Application}
certificate=${certificate:-Apple Development}
identity=
for candidate in $(security find-identity -v -p codesigning | sed -n "s/.*) \([0-9A-F]\{40\}\) \"$certificate.*/\1/p"); do
    of=$(security find-certificate -a -Z -p | awk -v want="$candidate" '/SHA-1 hash:/ { keep = ($3 == want) } keep' |
        openssl x509 -noout -subject 2> /dev/null | sed -n 's/.*OU *= *\([A-Z0-9]*\).*/\1/p')
    if [ "$of" = "$team" ]; then
        identity=$candidate
    fi
done
if [ -z "$identity" ]; then
    echo "No $certificate certificate of the team $team is on this Mac to sign the app with" >&2
    exit 1
fi
signed=mac/build/sign.log
for part in app/Gates widgets/Widgets; do
    sed "s/com\.uwyn\.gatesofhumanity/$identifier/" "mac/$part.entitlements" > "mac/build/$(basename "$part").entitlements"
done
store=com.apple.developer.ubiquity-kvstore-identifier
profile=
if [ -z "${GATES_PROBED:-}" ]; then
    profile=$(python3 mac/profile.py "$team.$identifier" "$identity" ${GATES_RELEASED:+released})
fi
if [ -n "$profile" ]; then
    cp "$profile" "$app/Contents/embedded.provisionprofile"
    # what a profile is matched to the app by
    /usr/libexec/PlistBuddy -c "Add :com.apple.application-identifier string $team.$identifier" \
        -c "Add :com.apple.developer.team-identifier string $team" mac/build/Gates.entitlements
elif [ -n "${GATES_RELEASED:-}" ]; then
    echo "No Developer ID provisioning profile for $team.$identifier with iCloud is on this Mac, see mac/profile.py" >&2
    exit 1
else
    # a Mac refuses to start an app that says it's let into iCloud without a profile that agrees
    /usr/libexec/PlistBuddy -c "Delete :$store" mac/build/Gates.entitlements
fi
sign=(codesign -f -s "$identity" --options runtime ${GATES_RELEASED:+--timestamp})
"${sign[@]}" "$app/Contents/Frameworks/gatesofhumanity.framework" 2> "$signed" &&
    "${sign[@]}" --entitlements mac/build/Widgets.entitlements "$widgets" 2>> "$signed" &&
    "${sign[@]}" --entitlements mac/build/Gates.entitlements "$app" 2>> "$signed" ||
    { cat "$signed" >&2; exit 1; }
echo "Built $app, version $version build $build, $(du -sh "$app" | cut -f1 | tr -d ' '), $([ -n "$profile" ] && echo "signed for iCloud" || echo "not signed for iCloud: its people stay on this Mac")"
