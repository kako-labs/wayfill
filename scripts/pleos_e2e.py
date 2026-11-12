#!/usr/bin/env python3
"""Bounded Fuel Pilot PLEOS scenario driver; status is the read-only default."""

from __future__ import annotations

import argparse
from datetime import datetime, timezone
import json
import os
import re
import sys
import time
from typing import Sequence

import pleos_probe

PACKAGE = "dev.fuelpilot"

SCENARIOS = {
    "motion-p-d-unknown": ("debug-partial", "AAOS gear + CarUxRestrictions adapter exists; PLEOS execution unverified; debug P/D available"),
    "app-visible-nav-foreground": ("automated", "foreground focus only"),
    "hun-two-actions": ("source-only", "CATEGORY_NAVIGATION and two actions exist; gateway lacks the verified recommendation/target chain"),
    "no-action-timeout-dismissal": ("source-only", "needs a real HUN on the target"),
    "permission-revocation": ("automated", "revokes and optionally restores one granted runtime permission"),
    "car-service-restart": ("blocked", "AAOS/FusedLocation/NaviHelper adapters exist, but no PLEOS restart/reconnect evidence exists"),
    "route-invalidation": ("source-only", "NaviHelper adapter exists; gateway lacks verified WGS84/road metrics/NavigationTarget so commands fail closed"),
    "refuel-event": ("debug-only", "synthetic debug instrumentation only; no production refuel source"),
}


def evidence(status: str, scenario: str, **values: object) -> dict[str, object]:
    result: dict[str, object] = {
        "schema": "fuel-pilot-pleos-e2e/v1",
        "captured_at_utc": datetime.now(timezone.utc).isoformat(),
        "scenario": scenario,
        "status": status,
    }
    result.update(values)
    return result


def require_apply(args: argparse.Namespace, debug: bool = False) -> None:
    if not args.apply:
        raise PermissionError("state-changing scenarios require --apply")
    if debug and not args.ack_debug_synthetic:
        raise PermissionError("debug scenarios also require --ack-debug-synthetic")


def connected_adb(args: argparse.Namespace) -> tuple[pleos_probe.Adb, dict[str, object]]:
    executable, _ = pleos_probe.find_adb(args.adb)
    if not executable:
        raise RuntimeError("adb was not found")
    host = pleos_probe.Adb(executable, timeout=args.timeout)
    devices = pleos_probe.parse_adb_devices(pleos_probe.text_of(host.run("devices", "-l")))
    serial = pleos_probe.choose_serial(devices, args.serial or os.environ.get("ANDROID_SERIAL"))
    adb = pleos_probe.Adb(executable, serial, args.timeout)
    probe = pleos_probe.probe_device(adb, args.package)
    if not probe["device"]["is_emulator"] and not args.allow_physical_device:  # type: ignore[index]
        raise PermissionError("physical/non-emulator targets require --allow-physical-device")
    return adb, probe


def current_focus(adb: pleos_probe.Adb) -> str | None:
    windows = pleos_probe.shell_text(adb, "dumpsys", "window", "windows", timeout=20)
    found = re.search(r"mCurrentFocus=Window\{[^}]*\s([^\s}]+)\}", windows)
    return found.group(1) if found else None


def run_visibility(args: argparse.Namespace, adb: pleos_probe.Adb, probe: dict[str, object]) -> dict[str, object]:
    user = str(probe["users"]["current"])  # type: ignore[index]
    navigation = args.nav_component or probe["navigation"]["component"]  # type: ignore[index]
    if not navigation:
        return evidence("BLOCKED", args.scenario, reason="no navigation activity resolved; pass --nav-component")
    app = adb.shell("am", "start", "-W", "--user", user, "-n", f"{args.package}/.MainActivity", timeout=40)
    time.sleep(args.settle_seconds)
    app_focus = current_focus(adb)
    nav = adb.shell("am", "start", "-W", "--user", user, "-n", str(navigation), timeout=40)
    time.sleep(args.settle_seconds)
    nav_focus = current_focus(adb)
    passed = app.returncode == nav.returncode == 0 and args.package in str(app_focus) and str(navigation).split("/", 1)[0] in str(nav_focus)
    return evidence(
        "PASS" if passed else "FAIL",
        args.scenario,
        app_focus=app_focus,
        navigation_focus=nav_focus,
        claim_limit="foreground ownership only; no HUN or route result inferred",
    )


def permission_state(adb: pleos_probe.Adb, package: str, permission: str) -> bool | None:
    dump = pleos_probe.shell_text(adb, "dumpsys", "package", package, timeout=25)
    values = re.findall(re.escape(permission) + r": granted=(true|false)", dump)
    return values[-1] == "true" if values else None


def run_permission(args: argparse.Namespace, adb: pleos_probe.Adb, probe: dict[str, object]) -> dict[str, object]:
    user = str(probe["users"]["current"])  # type: ignore[index]
    before = permission_state(adb, args.package, args.permission)
    if before is not True:
        return evidence("BLOCKED", args.scenario, reason="permission is not currently granted", before=before)
    revoked = adb.shell("pm", "revoke", "--user", user, args.package, args.permission, timeout=25)
    time.sleep(args.settle_seconds)
    after = permission_state(adb, args.package, args.permission)
    restored = None
    if args.restore_permission:
        adb.shell("pm", "grant", "--user", user, args.package, args.permission, timeout=25)
        restored = permission_state(adb, args.package, args.permission)
    passed = revoked.returncode == 0 and after is False and (restored is True if args.restore_permission else True)
    return evidence("PASS" if passed else "FAIL", args.scenario, permission=args.permission,
                    before=before, after_revoke=after, restored=restored)


def run_debug(args: argparse.Namespace, adb: pleos_probe.Adb, probe: dict[str, object]) -> dict[str, object]:
    test = {
        "motion-p-d-unknown": "dev.fuelpilot.NativeDemoTest#movingCancelsAndDuplicateRefuelDoesNotReplay",
        "refuel-event": "dev.fuelpilot.NativeDemoTest#movingCancelsAndDuplicateRefuelDoesNotReplay",
    }[args.scenario]
    listing = pleos_probe.shell_text(adb, "pm", "list", "instrumentation")
    match = re.search(r"instrumentation:([^\s]+)\s+\(target=" + re.escape(args.package) + r"\)", listing)
    if not match:
        return evidence("BLOCKED", args.scenario, reason="debug instrumentation is not installed")
    result = adb.shell("am", "instrument", "-w", "-r", "--user", str(probe["users"]["current"]),  # type: ignore[index]
                       "-e", "class", test, match.group(1), timeout=args.instrumentation_timeout)
    output = pleos_probe.text_of(result)
    passed = result.returncode == 0 and "OK (1 test)" in output and "FAILURES!!!" not in output
    return evidence("PASS_DEBUG_SYNTHETIC" if passed else "FAIL_DEBUG_SYNTHETIC", args.scenario,
                    test=test, label="FUEL_PILOT_SYNTHETIC_ONLY · not production evidence", output=output[-8000:])


def parse_args(argv: Sequence[str] | None = None) -> argparse.Namespace:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--scenario", choices=("status", *SCENARIOS), default="status")
    parser.add_argument("--apply", action="store_true", help="required for every state-changing adb action")
    parser.add_argument("--ack-debug-synthetic", action="store_true")
    parser.add_argument("--allow-physical-device", action="store_true")
    parser.add_argument("--restore-permission", action="store_true")
    parser.add_argument("--permission", default="android.permission.POST_NOTIFICATIONS")
    parser.add_argument("--package", default=PACKAGE)
    parser.add_argument("--serial")
    parser.add_argument("--adb")
    parser.add_argument("--nav-component")
    parser.add_argument("--timeout", type=int, default=15)
    parser.add_argument("--settle-seconds", type=float, default=2.0)
    parser.add_argument("--instrumentation-timeout", type=int, default=180)
    args = parser.parse_args(argv)
    if not 0 <= args.settle_seconds <= 30:
        parser.error("--settle-seconds must be between 0 and 30")
    return args


def main(argv: Sequence[str] | None = None) -> int:
    args = parse_args(argv)
    if args.scenario == "status" or not args.apply:
        payload = evidence("READ_ONLY_STATUS", args.scenario, apply_requested=args.apply,
                           scenarios={name: {"support": value[0], "note": value[1]} for name, value in SCENARIOS.items()})
        print(json.dumps(payload, ensure_ascii=False, indent=2, sort_keys=True))
        return 0
    support = SCENARIOS[args.scenario][0]
    if support in ("source-only", "blocked"):
        print(json.dumps(evidence("BLOCKED", args.scenario, reason=SCENARIOS[args.scenario][1]), ensure_ascii=False, indent=2))
        return 4
    try:
        require_apply(args, debug=support.startswith("debug"))
        adb, probe = connected_adb(args)
        if args.scenario == "app-visible-nav-foreground":
            result = run_visibility(args, adb, probe)
        elif args.scenario == "permission-revocation":
            result = run_permission(args, adb, probe)
        else:
            result = run_debug(args, adb, probe)
    except (PermissionError, RuntimeError, ValueError) as error:
        result = evidence("BLOCKED", args.scenario, reason=str(error))
    print(json.dumps(result, ensure_ascii=False, indent=2, sort_keys=True))
    return 0 if str(result["status"]).startswith("PASS") else 4


if __name__ == "__main__":
    raise SystemExit(main())
