#!/usr/bin/env python3
"""Finds the provisioning profile on this Mac that lets the app into iCloud as it's signed.

    mac/profile.py APP_ID CERTIFICATE [released]

APP_ID is the team and the bundle identifier, CERTIFICATE the SHA-1 of what signs the app.
A released app takes a profile for every Mac, of the Developer ID certificate; one for this
Mac alone takes a development profile that names it. Prints the path of the one that lasts
longest, or nothing: iCloud only lets an app in that carries such a profile.

A Developer ID profile is made once in the developer account (Profiles, Developer ID, the
app's identifier) or through the App Store Connect API (profileType MAC_APP_DIRECT), and
saved among Xcode's. Xcode makes the development one when it builds the Mac target."""
import datetime
import glob
import hashlib
import os
import plistlib
import subprocess
import sys

FOLDERS = ("~/Library/Developer/Xcode/UserData/Provisioning Profiles", "~/Library/MobileDevice/Provisioning Profiles")
STORE = "com.apple.developer.ubiquity-kvstore-identifier"


def main():
    if len(sys.argv) not in (3, 4):
        sys.exit(__doc__)
    app, certificate, released = sys.argv[1], sys.argv[2].lower(), len(sys.argv) == 4
    this = ""
    if not released:
        hardware = subprocess.run(["system_profiler", "SPHardwareDataType"], text=True, capture_output=True).stdout
        this = next((line.split(":", 1)[1].strip() for line in hardware.splitlines() if "Provisioning UDID" in line), "")
    found = []
    for folder in FOLDERS:
        for path in glob.glob(os.path.join(os.path.expanduser(folder), "*.provisionprofile")):
            read = subprocess.run(["security", "cms", "-D", "-i", path], capture_output=True)
            if read.returncode != 0:
                continue
            profile = plistlib.loads(read.stdout)
            entitled = profile.get("Entitlements", {})
            if entitled.get("com.apple.application-identifier") != app or STORE not in entitled:
                continue
            if profile["ExpirationDate"] < datetime.datetime.now(profile["ExpirationDate"].tzinfo):
                continue
            if certificate not in [hashlib.sha1(each).hexdigest() for each in profile.get("DeveloperCertificates", [])]:
                continue
            if released != bool(profile.get("ProvisionsAllDevices")) or (not released and this not in profile.get("ProvisionedDevices", [])):
                continue
            found.append((profile["ExpirationDate"], path))
    if found:
        print(max(found)[1])


if __name__ == "__main__":
    main()
