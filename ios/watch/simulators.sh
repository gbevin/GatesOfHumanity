# The simulator of an iPhone called $device and the one of the watch that's paired with it, as
# $phone and $watch, for the scripts that build and check the watch app.
phone=$(./bld app-simulator ios-sim "$device")
watch=$(xcrun simctl list pairs -j | python3 -c "
import json, sys
pairs = json.load(sys.stdin)['pairs'].values()
print(next((pair['watch']['udid'] for pair in pairs if pair['phone']['udid'] == sys.argv[1] and 'active' in pair['state']), ''))" "$phone")
if [ -z "$watch" ]; then
    echo "The simulator $device has no watch: pair one with xcrun simctl pair <watch> $phone" >&2
    exit 1
fi
