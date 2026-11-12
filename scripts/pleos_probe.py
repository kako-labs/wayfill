#!/usr/bin/env python3
"""Read-only inventory of an Android Automotive or Pleos target.

The probe never installs, starts, stops, grants, revokes, clears, or writes to a
device. JSON is printed to stdout; --output only copies that JSON to a host file.
"""

from __future__ import annotations

import argparse
import json
import os
from pathlib import Path
import re
import shutil
import subprocess
import sys
from datetime import datetime, timezone
from typing import Iterable, Mapping, Sequence

DEFAULT_PACKAGE = "dev.fuelpilot"
DEFAULT_TIMEOUT_SECONDS = 15


def find_adb(
    explicit: str | None = None,
    environ: Mapping[str, str] | None = None,
    which=shutil.which,
) -> tuple[str | None, str]:
    """Find adb without assuming a user or SDK installation path."""
    env = os.environ if environ is None else environ
    candidates: list[tuple[str | None, str]] = [
        (explicit, "--adb"),
        (env.get("PLEOS_ADB"), "PLEOS_ADB"),
        (which("adb"), "PATH"),
    ]
    executable = "adb.exe" if os.name == "nt" else "adb"
    for variable in ("ANDROID_HOME", "ANDROID_SDK_ROOT"):
        root = env.get(variable)
        candidates.append((str(Path(root) / "platform-tools" / executable) if root else None, variable))
    for candidate, source in candidates:
        if not candidate:
            continue
        path = Path(candidate)
        if path.is_file() or source == "PATH":
            return str(path), source
    return None, "not-found"


def parse_adb_devices(text: str) -> list[dict[str, str]]:
    devices: list[dict[str, str]] = []
    for raw in text.splitlines():
        line = raw.strip()
        if not line or line.startswith("List of devices") or line.startswith("*"):
            continue
        fields = line.split()
        if len(fields) < 2:
            continue
        record = {"serial": fields[0], "state": fields[1]}
        for item in fields[2:]:
            if ":" in item:
                key, value = item.split(":", 1)
                record[key] = value
        devices.append(record)
    return devices


def choose_serial(devices: Sequence[Mapping[str, str]], requested: str | None) -> str:
    ready = [device["serial"] for device in devices if device.get("state") == "device"]
    if requested:
        matches = [device for device in devices if device.get("serial") == requested]
        if not matches:
            raise ValueError(f"requested serial is not attached: {requested}")
        if matches[0].get("state") != "device":
            raise ValueError(f"requested serial is not ready: {requested} ({matches[0].get('state')})")
        return requested
    if len(ready) != 1:
        raise ValueError(f"set --serial or ANDROID_SERIAL; ready devices: {ready}")
    return ready[0]


def bounded_lines(text: str, patterns: Iterable[str] = (), limit: int = 80) -> list[str]:
    compiled = [re.compile(pattern, re.IGNORECASE) for pattern in patterns]
    lines = [line.rstrip() for line in text.splitlines()]
    if compiled:
        lines = [line for line in lines if any(pattern.search(line) for pattern in compiled)]
    return lines[:limit]


class Adb:
    def __init__(self, executable: str, serial: str | None = None, timeout: int = DEFAULT_TIMEOUT_SECONDS):
        self.executable = executable
        self.serial = serial
        self.timeout = timeout

    def run(self, *parts: str, timeout: int | None = None) -> subprocess.CompletedProcess[str]:
        command = [self.executable]
        if self.serial:
            command += ["-s", self.serial]
        command += list(parts)
        return subprocess.run(
            command,
            capture_output=True,
            text=True,
            errors="replace",
            timeout=timeout or self.timeout,
            check=False,
        )

    def shell(self, *parts: str, timeout: int | None = None) -> subprocess.CompletedProcess[str]:
        return self.run("shell", *parts, timeout=timeout)


def text_of(result: subprocess.CompletedProcess[str]) -> str:
    return (result.stdout + ("\n" + result.stderr if result.stderr else "")).strip()


def shell_text(adb: Adb, *parts: str, timeout: int | None = None) -> str:
    try:
        return text_of(adb.shell(*parts, timeout=timeout))
    except (OSError, subprocess.TimeoutExpired) as error:
        return f"PROBE_ERROR: {type(error).__name__}: {error}"


def property_value(adb: Adb, name: str) -> str | None:
    value = shell_text(adb, "getprop", name).strip()
    return value or None


def resolve_navigation(adb: Adb, user: str) -> dict[str, str | None]:
    command = (
        "cmd", "package", "resolve-activity", "--brief", "--user", user,
        "-a", "android.intent.action.VIEW", "-d", "geo:0,0?q=fuel",
    )
    result = shell_text(adb, *command)
    component = next(
        (line.strip() for line in result.splitlines() if "/" in line and "No activity" not in line),
        None,
    )
    return {"component": component, "raw": result[:1000]}


def package_inventory(adb: Adb, package: str, user: str) -> dict[str, object]:
    dump = shell_text(adb, "dumpsys", "package", package, timeout=25)
    missing = "Unable to find package" in dump or "Unknown package" in dump
    permissions = bounded_lines(
        dump,
        patterns=(r"uses-permission", r"granted=(true|false)", r"permission\.", r"FOREGROUND_SERVICE"),
        limit=120,
    )
    version = re.search(r"versionName=([^\s]+)", dump)
    services = shell_text(adb, "dumpsys", "activity", "services", package, timeout=20)
    notifications = shell_text(adb, "dumpsys", "notification", timeout=25)
    channel_lines = bounded_lines(
        notifications,
        patterns=(re.escape(package), r"monitoring", r"fuel_recommendations", r"NotificationChannel"),
        limit=100,
    )
    return {
        "package": package,
        "user": user,
        "installed": not missing,
        "version_name": version.group(1) if version else None,
        "permissions": permissions,
        "services": bounded_lines(services, patterns=(re.escape(package), r"MonitoringService", r"foreground"), limit=80),
        "notification_channels": channel_lines,
    }


def probe_device(adb: Adb, package: str = DEFAULT_PACKAGE) -> dict[str, object]:
    current_user = shell_text(adb, "am", "get-current-user").splitlines()[0].strip() or "0"
    features = shell_text(adb, "pm", "list", "features")
    packages = shell_text(adb, "pm", "list", "packages", "-f")
    services = shell_text(adb, "service", "list", timeout=25)
    car_dump = shell_text(adb, "dumpsys", "car_service", timeout=25)
    windows = shell_text(adb, "dumpsys", "window", "windows", timeout=20)
    focus = re.search(r"mCurrentFocus=Window\{[^}]*\s([^\s}]+)\}", windows)
    qemu = property_value(adb, "ro.kernel.qemu")
    hardware = property_value(adb, "ro.hardware")
    automotive = "android.hardware.type.automotive" in features
    pleos_markers = bounded_lines(
        packages,
        patterns=(r"pleos", r"umos", r"42dot", r"vehiclecontrol", r"navigation"),
        limit=80,
    )
    return {
        "captured_at_utc": datetime.now(timezone.utc).isoformat(),
        "serial": adb.serial,
        "device": {
            "manufacturer": property_value(adb, "ro.product.manufacturer"),
            "model": property_value(adb, "ro.product.model"),
            "product": property_value(adb, "ro.product.name"),
            "build_fingerprint": property_value(adb, "ro.build.fingerprint"),
            "sdk": property_value(adb, "ro.build.version.sdk"),
            "hardware": hardware,
            "is_emulator": qemu == "1" or bool(hardware and "ranchu" in hardware.lower()),
            "is_automotive": automotive,
            "pleos_package_markers": pleos_markers,
        },
        "users": {
            "current": current_user,
            "list": bounded_lines(shell_text(adb, "pm", "list", "users"), limit=30),
        },
        "car_service": {
            "registered_services": bounded_lines(services, patterns=(r"car", r"vehicle"), limit=80),
            "dump_available": "PROBE_ERROR" not in car_dump and "Can't find service" not in car_dump,
            "summary": bounded_lines(
                car_dump,
                patterns=(r"CarService", r"Vehicle", r"HAL", r"user", r"state", r"permission"),
                limit=80,
            ),
        },
        "navigation": resolve_navigation(adb, current_user),
        "focus": focus.group(1) if focus else None,
        "fuel_pilot": package_inventory(adb, package, current_user),
    }


def build_inventory(args: argparse.Namespace) -> tuple[dict[str, object], int]:
    executable, source = find_adb(args.adb)
    inventory: dict[str, object] = {
        "schema": "fuel-pilot-pleos-probe/v1",
        "probe_mode": "read-only",
        "apply": {
            "requested": args.apply,
            "performed": False,
            "note": "This revision has no mutation implementation. --apply is reserved as the mandatory safety latch for future state-changing probes.",
        },
        "adb": {"found": bool(executable), "discovery": source},
    }
    if not executable:
        inventory["status"] = "BLOCKED_NO_ADB"
        return inventory, 2
    host_adb = Adb(executable, timeout=args.timeout)
    version_lines = text_of(host_adb.run("version")).splitlines()
    if not args.include_host_paths:
        version_lines = [line for line in version_lines if not line.lower().startswith("installed as ")]
    devices_text = text_of(host_adb.run("devices", "-l"))
    devices = parse_adb_devices(devices_text)
    inventory["adb"] = {
        "found": True,
        "discovery": source,
        "executable": executable if args.include_host_paths else Path(executable).name,
        "version": version_lines[:3],
        "devices": devices,
    }
    try:
        serial = choose_serial(devices, args.serial or os.environ.get("ANDROID_SERIAL"))
    except ValueError as error:
        inventory["status"] = "BLOCKED_DEVICE_SELECTION"
        inventory["reason"] = str(error)
        return inventory, 3
    device_adb = Adb(executable, serial, args.timeout)
    inventory["status"] = "OBSERVED"
    inventory["target"] = probe_device(device_adb, args.package)
    return inventory, 0


def parse_args(argv: Sequence[str] | None = None) -> argparse.Namespace:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--adb", help="adb executable; otherwise discover from PLEOS_ADB, PATH, or Android SDK env")
    parser.add_argument("--serial", help="target serial; otherwise use ANDROID_SERIAL or the only ready device")
    parser.add_argument("--package", default=DEFAULT_PACKAGE)
    parser.add_argument("--timeout", type=int, default=DEFAULT_TIMEOUT_SECONDS)
    parser.add_argument("--output", type=Path, help="optional host JSON copy; stdout is always written")
    parser.add_argument("--include-host-paths", action="store_true", help="include the resolved adb host path")
    parser.add_argument(
        "--apply",
        action="store_true",
        help="explicit mutation safety latch; no mutation is implemented in this read-only revision",
    )
    args = parser.parse_args(argv)
    if not 1 <= args.timeout <= 120:
        parser.error("--timeout must be between 1 and 120 seconds")
    return args


def main(argv: Sequence[str] | None = None) -> int:
    args = parse_args(argv)
    inventory, code = build_inventory(args)
    payload = json.dumps(inventory, ensure_ascii=False, indent=2, sort_keys=True) + "\n"
    sys.stdout.write(payload)
    if args.output:
        args.output.parent.mkdir(parents=True, exist_ok=True)
        args.output.write_text(payload, encoding="utf-8")
    return code


if __name__ == "__main__":
    raise SystemExit(main())
