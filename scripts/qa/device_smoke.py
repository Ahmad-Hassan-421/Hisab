"""Exercise a few UI entry points on an authorized device with the QA APK."""

import argparse
import re
import subprocess
import time
import xml.etree.ElementTree as ET

QA_PACKAGE = "com.example.hisab.qa"
ACTIVITY = "com.example.hisab.ui.MainActivity"
UI_FILE = "/data/local/tmp/hisab_qa_smoke.xml"


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--adb", default="adb")
    parser.add_argument("--serial", required=True)
    args = parser.parse_args()

    def adb(*command):
        return subprocess.check_output(
            [args.adb, "-s", args.serial, *command], encoding="utf-8", errors="replace", timeout=30
        )

    def screen():
        adb("shell", "uiautomator", "dump", UI_FILE)
        root = ET.fromstring(adb("shell", "cat", UI_FILE))
        return root.findall(f".//node[@package='{QA_PACKAGE}']")

    def label(node):
        return node.get("text") or node.get("content-desc")

    def wait_for(expected):
        for _ in range(4):
            for node in screen():
                if label(node) == expected:
                    return node
            time.sleep(0.5)
        raise RuntimeError(f"Expected QA screen control was unavailable: {expected}")

    def tap(expected):
        node = wait_for(expected)
        left, top, right, bottom = map(int, re.findall(r"\d+", node.get("bounds")))
        adb("shell", "input", "tap", str((left + right) // 2), str((top + bottom) // 2))

    adb("shell", "am", "start", "-W", "-n", f"{QA_PACKAGE}/{ACTIVITY}")
    time.sleep(1)
    visible = {label(node) for node in screen()}
    if "Log In" in visible:
        tap("Log In")
        wait_for("Please enter a valid email address")
        tap("Sign Up")
        wait_for("Confirm Password")
        tap("Log In")
        wait_for("Welcome Back")
        print("PASS QA login, empty-form validation, and signup navigation")
    elif "Create Hisab" in visible:
        tap("Notifications")
        wait_for("Back")
        wait_for("Notifications")
        tap("Back")
        tap("Profile")
        wait_for("Save Profile")
        tap("Back")
        wait_for("Create Hisab")
        print("PASS QA dashboard, notifications, profile, and back navigation")
    else:
        raise RuntimeError("Open the unlocked device and install the isolated Hisab QA APK before testing.")


if __name__ == "__main__":
    main()
