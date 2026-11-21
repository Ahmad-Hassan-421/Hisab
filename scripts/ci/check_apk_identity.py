"""Check the installed identity represented by Android SDK aapt output."""

import argparse
import re
import sys


def check(badging, package, label, debuggable, version, previous):
    manifest = re.search(r"^package: name='([^']+)' versionCode='(\d+)' versionName='([^']+)'", badging, re.M)
    launchers = re.findall(r"^launchable-activity: name='([^']+)'", badging, re.M)
    actual_label = re.search(r"^application-label:'([^']+)'", badging, re.M)
    if manifest is None or manifest[1] != package:
        raise ValueError(f"Expected application ID {package}")
    if actual_label is None or actual_label[1] != label:
        raise ValueError(f"Expected launcher label {label!r}")
    if launchers != ["com.example.hisab.ui.MainActivity"]:
        raise ValueError("Expected exactly one Hisab launcher activity")
    if bool(re.search(r"^application-debuggable\b", badging, re.M)) != debuggable:
        raise ValueError("APK debuggable flag differs from the intended build variant")
    code = int(manifest[2])
    if not 0 < code <= 2_100_000_000 or (previous is not None and code <= previous):
        raise ValueError("APK build number must be valid and newer than the previous release")
    if version is not None and manifest[3] != version:
        raise ValueError(f"Expected APK version {version}")
    return code, manifest[3]


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--package", required=True)
    parser.add_argument("--label", required=True)
    parser.add_argument("--debuggable", action="store_true")
    parser.add_argument("--version")
    parser.add_argument("--previous", type=int)
    args = parser.parse_args()
    try:
        code, version = check(sys.stdin.read(), args.package, args.label, args.debuggable, args.version, args.previous)
    except ValueError as error:
        parser.exit(1, f"{error}\n")
    print(f"Verified {args.package} {version} (build {code})")


if __name__ == "__main__":
    main()
