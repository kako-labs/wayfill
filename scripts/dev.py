#!/usr/bin/env python3
"""Portable developer entry point using only the Python standard library."""
import argparse
import os
from pathlib import Path
import re
import shutil
import subprocess
import sys

ROOT = Path(__file__).resolve().parents[1]


def java_command():
    if os.environ.get("JAVA_HOME"):
        return str(Path(os.environ["JAVA_HOME"]) / "bin" / ("java.exe" if os.name == "nt" else "java"))
    return shutil.which("java")


def doctor():
    errors = []
    java = java_command()
    if not java or not Path(java).is_file():
        errors.append("Java was not found. Install JDK 17.")
    else:
        result = subprocess.run([java, "-version"], capture_output=True, text=True)
        if result.returncode or not re.search(r'version "17[.\"]', result.stdout + result.stderr):
            errors.append("JDK 17 is required. Set JAVA_HOME to JDK 17.")
    sdk = os.environ.get("ANDROID_HOME") or os.environ.get("ANDROID_SDK_ROOT")
    if not sdk or not Path(sdk).is_dir():
        errors.append("Set ANDROID_HOME or ANDROID_SDK_ROOT to the Android SDK.")
    for error in errors:
        print("ERROR:", error)
    if errors:
        return 1
    print("Ready: JDK 17, Android SDK, and Gradle wrapper.")
    return 0


parser = argparse.ArgumentParser(description=__doc__)
parser.add_argument("command", choices=["doctor", "verify"])
args = parser.parse_args()
if args.command == "doctor":
    sys.exit(doctor())
if doctor():
    sys.exit(1)
wrapper = ROOT / ("gradlew.bat" if os.name == "nt" else "gradlew")
sys.exit(subprocess.call([str(wrapper), "verifyAll", "--no-daemon"], cwd=ROOT))
