#!/usr/bin/env bash
# Builds the app as it's released and installs it on the devices that are connected or on the
# same network: the iOS app on an iPhone or an iPad, the app for visionOS on an Apple Vision
# Pro. Nothing is published and the build number stays as it is.
#
#   ios/install.sh [part of the name of a device]...     "iPhone 17" by default
set -euo pipefail

cd "$(dirname "$0")"
if [ $# -eq 0 ]; then
    set -- "iPhone 17"
fi

phones=()
visions=()
for device in "$@"; do
    # a headset that lies on a table is "connected", which can be reached as "available" can
    listed=$(xcrun devicectl list devices 2> /dev/null | grep -F "$device" | grep -E " (available \(paired\)|connected) " | head -1 || true)
    udid=$(printf '%s' "$listed" | sed -E 's/.* ([0-9A-F-]{20,}) .*/\1/')
    if [ -z "$udid" ]; then
        echo "No device called $device can be reached" >&2
    elif [[ "$listed" == *"(RealityDevice"* ]]; then
        visions+=("$udid")
    else
        phones+=("$udid")
    fi
done
if [ ${#phones[@]} -eq 0 ] && [ ${#visions[@]} -eq 0 ]; then
    exit 1
fi

# signs in with the API key rather than Xcode's account, whose session expires
keys=("$HOME"/.appstoreconnect/private_keys/AuthKey_*.p8)
if [ ${#keys[@]} -ne 1 ] || [ ! -f "${keys[0]}" ] || [ ! -f "$HOME/.appstoreconnect/issuer_id" ]; then
    echo "Needs one AuthKey_<id>.p8 in ~/.appstoreconnect/private_keys and the issuer ID in ~/.appstoreconnect/issuer_id" >&2
    exit 1
fi
key=$(basename "${keys[0]}" .p8)

failed=0
# Builds a scheme of the project for any device of a system rather than for these, since Xcode
# doesn't always list a device that can be reached, and installs what it built on each.
install() {
    local scheme=$1 system=$2 built=$3 app=$4
    shift 4
    xcodebuild -project GatesOfHumanity.xcodeproj -scheme "$scheme" -configuration Release -destination "generic/platform=$system" \
        -derivedDataPath "$built" -allowProvisioningUpdates -authenticationKeyPath "${keys[0]}" \
        -authenticationKeyID "${key#AuthKey_}" -authenticationKeyIssuerID "$(cat "$HOME/.appstoreconnect/issuer_id")" \
        build > "$built.log" 2>&1 || { echo "The app didn't build, see ${built#../}.log" >&2; exit 1; }
    app="$built/Build/Products/$app"
    for udid in "$@"; do
        name=$(xcrun devicectl list devices 2> /dev/null | grep -F "$udid" | sed -E 's/  +.*//')
        if xcrun devicectl device install app --device "$udid" "$app" > "$built-device.log" 2>&1; then
            echo "Installed $(/usr/libexec/PlistBuddy -c 'Print :CFBundleShortVersionString' "$app/Info.plist") build $(/usr/libexec/PlistBuddy -c 'Print :CFBundleVersion' "$app/Info.plist") on $name"
        else
            echo "The app didn't install on $name, see ${built#../}-device.log" >&2
            failed=1
        fi
    done
}

# the library that Xcode links has every system in it that was built
if [ ${#phones[@]} -ne 0 ]; then
    (cd .. && ./bld app-library ios)
fi
if [ ${#visions[@]} -ne 0 ]; then
    (cd .. && ./bld app-library vision)
fi
xcodegen generate > /dev/null
if [ ${#phones[@]} -ne 0 ]; then
    install GatesOfHumanity iOS build/install Release-iphoneos/GatesOfHumanity.app "${phones[@]}"
fi
if [ ${#visions[@]} -ne 0 ]; then
    install Vision visionOS ../vision/build/install Release-xros/Gates.app "${visions[@]}"
fi
exit $failed
