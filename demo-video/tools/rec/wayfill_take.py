"""Records the WAYFILL story in one take, in the order the video shows it: the navigation is guiding to the
destination while parked, WAYFILL opens from the dock and judges no stop is needed, monitoring starts, the car
drives with WAYFILL behind the navigation, the range drops (Virtual Console) until the reserve no longer holds,
the heads-up card and voice ask about a station on the route, "바로 안내" adds it as a navigation waypoint, WAYFILL
shows the stop is being guided, and the car parks.

usage: python3 wayfill_take.py <out_dir>
Prepare first (the filming helper FilmNaviTest sets the destination; see README.md). Only touches the AVD
"pleos" on emulator-5590 (gRPC 8592, Virtual Console forwarded to 18088). Writes screen.mp4/screen.json (1920x1080,
30 fps), audio.rec/audio.json, logcat.txt, events.json (marks, taps on the 2560x1440 display, console inputs, the
card text) and clock.json (emulator minus host clock, measured three times).
"""
import json
import os
import re
import signal
import subprocess
import sys
import threading
import time
import urllib.request
import xml.etree.ElementTree as ET

OUT = os.path.abspath(sys.argv[1])
os.makedirs(OUT, exist_ok=True)
HERE = os.path.dirname(os.path.abspath(__file__))
SERIAL = "emulator-5590"
ADB = [os.path.expanduser("~/Library/Android/sdk/platform-tools/adb"), "-s", SERIAL]
APP = "dev.fuelpilot/.MainActivity"
CONSOLE = "http://127.0.0.1:18088"
# Dock icons on the 2560x1440 display while WAYFILL runs (six icons; measured 2026-11-14).
DOCK_NAV = (1097, 1365)
DOCK_APP = (1463, 1365)
# "바로 안내" on the heads-up card over the navigation.
HUN_GUIDE = (1445, 257)
START_RANGE_KM = 80
END_RANGE_KM = 70
events = []


def now():
    return int(time.time() * 1000)


def log(kind, **data):
    events.append({"t": now(), "kind": kind, **data})
    print(f"[{kind}]", json.dumps(data, ensure_ascii=False), flush=True)


def mark(name):
    log("mark", name=name)


def adb(*args, timeout=60):
    return subprocess.run(ADB + list(args), capture_output=True, text=True, timeout=timeout).stdout


def shell(cmd, timeout=60):
    return adb("shell", cmd, timeout=timeout)


def console(method, path, **logged):
    try:
        with urllib.request.urlopen(urllib.request.Request(CONSOLE + path, method=method), timeout=10) as r:
            status = r.status
    except Exception as e:  # noqa: BLE001
        status = str(e)
    if logged:
        log("console", status=status, **logged)
    return status


def nodes():
    shell("uiautomator dump /sdcard/ui.xml >/dev/null 2>&1")
    xml = shell("cat /sdcard/ui.xml")
    try:
        return list(ET.fromstring(xml[xml.index("<"):]).iter("node"))
    except Exception:  # noqa: BLE001
        return []


def center(node):
    x1, y1, x2, y2 = map(int, re.findall(r"\d+", node.get("bounds")))
    return (x1 + x2) // 2, (y1 + y2) // 2


def press(x, y, text, hold):
    # Logged just before it is sent, so the edit can draw where and when the tap landed.
    log("tap", text=text, x=x, y=y)
    shell(f"input tap {x} {y}")
    time.sleep(hold)


def tap(text, hold=2.0):
    for n in nodes():
        if n.get("text") == text:
            press(*center(n), text, hold)
            return True
    log("missing", text=text)
    return False


def active_notifications():
    return shell("dumpsys notification --noredact").split("mArchive=")[0]


def card_text():
    """The WAYFILL heads-up card while it is posted: '17.7km 앞 · 경로 +4분 · 미래주유소'."""
    active = active_notifications()
    if "바로 안내" not in active:
        return None
    found = re.search(r"android\.text=String \(([^)]*km 앞[^)]*)\)", active)
    return found.group(1) if found else ""


def wait(cond, limit, step=0.25):
    started = time.time()
    while time.time() - started < limit:
        if cond():
            return True
        time.sleep(step)
    return False


class Heartbeat(threading.Thread):
    """The Virtual Console drops inputs unless a client checks in at least every 3 s."""
    def __init__(self):
        super().__init__(daemon=True)
        self.stop_event = threading.Event()

    def run(self):
        while not self.stop_event.is_set():
            console("POST", "/browser/session?client=wayfill-take")
            self.stop_event.wait(1)


def clock_offsets():
    out = []
    for _ in range(3):
        host_before = time.time() * 1000
        device = float(shell("echo $EPOCHREALTIME").strip()) * 1000
        host_after = time.time() * 1000
        out.append(round(device - (host_before + host_after) / 2))
        time.sleep(0.2)
    return out


def start_recorders():
    env = {**os.environ, "EMU_GRPC": "8592"}
    procs = {
        "audio": subprocess.Popen(["node", "audio.js", os.path.join(OUT, "audio.rec"), os.path.join(OUT, "audio.json")], cwd=HERE, env=env),
        "screen": subprocess.Popen(["node", "screen.js", os.path.join(OUT, "screen.mp4"), os.path.join(OUT, "screen.json")], cwd=HERE, env=env),
        "logcat": subprocess.Popen(ADB + ["logcat", "-v", "epoch", "-T", "1"], stdout=open(os.path.join(OUT, "logcat.txt"), "w")),
    }
    time.sleep(3)
    return procs


def stop_recorders(procs):
    for name in ("screen", "audio", "logcat"):
        procs[name].send_signal(signal.SIGTERM)
    for name in ("screen", "audio"):
        procs[name].wait(timeout=90)


def story():
    # Parked, the navigation already guides to the destination; WAYFILL opens from the dock.
    mark("open")
    time.sleep(6)
    mark("app")
    # The home reads the car and the route for about 3 s; then it stays long enough to read the judgement.
    press(*DOCK_APP, "WAYFILL", 16)
    mark("monitor")
    tap("모니터링 시작", hold=5)

    # Back to the navigation, then drive. WAYFILL stays behind the navigation and keeps judging.
    mark("nav")
    press(*DOCK_NAV, "내비게이션", 6)
    mark("gear-d")
    console("PUT", "/vehicle/gear?gear=Drive", gear="Drive")
    console("PUT", "/vehicle/controls?speed=60&accelerator=20&brake=0&steering=0", speed=60)
    time.sleep(9)

    # The emulator's navigation never moves, so the range the drive would use is entered on the console.
    mark("drop")
    for km in range(START_RANGE_KM - 1, END_RANGE_KM - 1, -1):
        console("PUT", f"/vehicle/odometer?remaining_range_m={km * 1000}", range_km=km)
        time.sleep(1.2)
    mark("card-wait")
    shown = wait(lambda: card_text() is not None, 40)
    text = card_text() if shown else None
    log("card", shown=shown, text=text)
    # Let the voice finish (about 6 s); the card stays up to 15 s.
    time.sleep(7)

    mark("guide")
    press(*HUN_GUIDE, "바로 안내", 0.5)
    station = (text or "").split("·")[-1].strip()
    added = wait(lambda: any(station and station in (n.get("text") or "") for n in nodes()), 25, step=0.5)
    log("waypoint", station=station, shown=added)
    mark("waypoint")
    time.sleep(7)

    mark("app-again")
    press(*DOCK_APP, "WAYFILL", 8)

    mark("park")
    console("PUT", "/vehicle/controls?speed=0&accelerator=0&brake=30&steering=0", speed=0)
    time.sleep(2)
    console("PUT", "/vehicle/gear?gear=Park", gear="Park")
    time.sleep(10)
    mark("end")
    time.sleep(1)


def main():
    avd = adb("emu", "avd", "name").split()[0]
    print("avd:", avd, SERIAL)
    if avd != "pleos":
        sys.exit("not the pleos AVD")
    beat = Heartbeat()
    beat.start()
    time.sleep(1.5)
    offsets = clock_offsets()
    procs = start_recorders()
    try:
        story()
    finally:
        stop_recorders(procs)
        beat.stop_event.set()
        offsets += clock_offsets()
        json.dump(events, open(os.path.join(OUT, "events.json"), "w"), ensure_ascii=False, indent=1)
        json.dump({"emulatorMinusHostMs": offsets}, open(os.path.join(OUT, "clock.json"), "w"))
        print("clock offsets", offsets)


if __name__ == "__main__":
    main()
