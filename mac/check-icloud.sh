#!/usr/bin/env bash
# Checks that a Mac app is signed for iCloud in a way that a Mac accepts: that it says it's
# let into the key-value store, which keeps the saved people the same on someone's devices,
# under the app's own name, and that the provisioning profile inside it agrees. An app that
# says so without such a profile isn't started by a Mac at all, and one that doesn't say so
# runs and silently keeps its people to itself, which is how build 54 was sent for review.
#
#   mac/check-icloud.sh [APP]     mac/build/Gates.app otherwise
#
# With GATES_LOCAL set, an app that isn't signed for iCloud passes when it has no profile
# either: what build.sh makes for this Mac alone and for the checks.
set -euo pipefail

cd "$(dirname "$0")/.."
app=${1:-mac/build/Gates.app}
store=com.apple.developer.ubiquity-kvstore-identifier
team=$(sed -n 's/.*DEVELOPMENT_TEAM: *//p' ios/project.yml)
identifier=$(/usr/libexec/PlistBuddy -c "Print :CFBundleIdentifier" "$app/Contents/Info.plist")
signed=$(mktemp) allowed=$(mktemp)
trap 'rm -f "$signed" "$allowed"' EXIT
codesign -d --entitlements - --xml "$app" 2> /dev/null > "$signed"
says=$(/usr/libexec/PlistBuddy -c "Print :$store" "$signed" 2> /dev/null || true)
profile="$app/Contents/embedded.provisionprofile"
if [ -z "$says" ]; then
    if [ -n "${GATES_LOCAL:-}" ] && [ ! -e "$profile" ]; then
        echo "$app isn't signed for iCloud and has no profile: it keeps its people on this Mac"
        exit 0
    fi
    echo "$app isn't signed for iCloud: its people would stay on one Mac" >&2
    exit 1
fi
if [ "$says" != "$team.$identifier" ]; then
    echo "$app is signed for the iCloud store of $says, not of $team.$identifier" >&2
    exit 1
fi
if [ ! -e "$profile" ]; then
    echo "$app says it's let into iCloud and has no provisioning profile: a Mac won't start it" >&2
    exit 1
fi
security cms -D -i "$profile" 2> /dev/null | plutil -extract Entitlements xml1 -o "$allowed" -
lets=$(/usr/libexec/PlistBuddy -c "Print :$store" "$allowed" 2> /dev/null || true)
whose=$(/usr/libexec/PlistBuddy -c "Print :com.apple.application-identifier" "$allowed" 2> /dev/null || true)
case "$says" in
    $lets) ;;
    *) echo "The profile in $app lets it into the iCloud store of '$lets', not of $says" >&2; exit 1 ;;
esac
if [ "$whose" != "$team.$identifier" ]; then
    echo "The profile in $app is of $whose, not of $team.$identifier" >&2
    exit 1
fi
codesign --verify --deep --strict "$app"
echo "$app is signed for the iCloud store of $says, and the profile in it agrees"
