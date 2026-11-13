#!/usr/bin/env python3
"""Run Compose instrumentation on the managed Android35 device or a connected device."""
import argparse
import os
from pathlib import Path
import subprocess
import sys

ROOT = Path(__file__).resolve().parents[1]

def gradle_command(root, *, connected=False, windows=None):
    windows = os.name == 'nt' if windows is None else windows
    wrapper = Path(root) / ('gradlew.bat' if windows else 'gradlew')
    task = ':app:connectedDebugAndroidTest' if connected else ':app:pixel2api35DebugAndroidTest'
    return [str(wrapper), task, '--no-daemon', '--max-workers=1', '--console=plain']

def run(root=ROOT, *, connected=False):
    try:
        return subprocess.call(gradle_command(root, connected=connected), cwd=root)
    except OSError as error:
        print(f'Cannot start Gradle: {error}', file=sys.stderr)
        return 1

def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--connected', action='store_true', help='Use a running emulator/device instead of the managed device.')
    args = parser.parse_args()
    return run(connected=args.connected)

if __name__ == '__main__':
    sys.exit(main())
