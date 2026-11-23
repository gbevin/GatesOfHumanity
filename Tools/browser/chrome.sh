#!/usr/bin/env bash
# Starts or stops a headless Chrome for the scripts of Tools/browser, with a profile of its own that
# is deleted when it stops. CDP_PORT is its DevTools port, 9588 by default, and CHROME another Chrome
# or Chromium binary, which Linux and Windows need since the default is where macOS has it.
#
#   Tools/browser/chrome.sh start|stop
set -euo pipefail

port=${CDP_PORT:-9588}
chrome=${CHROME:-"/Applications/Google Chrome.app/Contents/MacOS/Google Chrome"}
# next to the downloads of the generators, where ./bld clean leaves it alone
state="$(dirname "$0")/../../.cache/browser"

case "${1:-}" in
start)
    if curl -sf -o /dev/null "http://127.0.0.1:$port/json/version"; then
        echo "Chrome already answers on port $port"
        exit 0
    fi
    rm -rf "$state" && mkdir -p "$state"
    "$chrome" --headless=new --remote-debugging-port="$port" --user-data-dir="$state/profile" \
        --no-first-run --no-default-browser-check >"$state/chrome.log" 2>&1 &
    echo $! > "$state/pid"
    for _ in $(seq 1 50); do
        if curl -sf -o /dev/null "http://127.0.0.1:$port/json/version"; then
            echo "Chrome answers on port $port"
            exit 0
        fi
        sleep 0.2
    done
    echo "Chrome didn't start, see $state/chrome.log" >&2
    exit 1
    ;;
stop)
    if [ ! -f "$state/pid" ]; then
        echo "No Chrome was started from here"
    fi
    if [ -f "$state/pid" ]; then
        pid=$(cat "$state/pid")
        kill "$pid" 2>/dev/null || true
        # Chrome writes its profile while it shuts down
        for _ in $(seq 1 50); do
            kill -0 "$pid" 2>/dev/null || break
            sleep 0.1
        done
    fi
    rm -rf "$state"
    ;;
*)
    echo "Usage: $0 start|stop" >&2
    exit 2
    ;;
esac
