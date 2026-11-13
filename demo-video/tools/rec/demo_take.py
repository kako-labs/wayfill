"""Records the after-refuel part with the app's demo mode (synthetic data): the real app home opens "시연 모드 · 예시
데이터", a full refuel event makes 뽀동이 greet once, and the record shows in 기록. The emulator car is electric, so no
real refuel signal exists; the edit labels this part as demo data and covers the demo's developer buttons.

usage: python3 demo_take.py <out_dir>   (start parked with the real WAYFILL home in front)
Writes screen.mp4/screen.json and events.json like wayfill_take.py.
"""
import json
import os
import signal
import subprocess
import sys
import time

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import wayfill_take as take  # noqa: E402

OUT = os.path.abspath(sys.argv[1])
os.makedirs(OUT, exist_ok=True)
take.OUT = OUT


def main():
    avd = take.adb("emu", "avd", "name").split()[0]
    if avd != "pleos":
        sys.exit("not the pleos AVD")
    env = {**os.environ, "EMU_GRPC": "8592"}
    screen = subprocess.Popen(["node", "screen.js", os.path.join(OUT, "screen.mp4"), os.path.join(OUT, "screen.json")],
                              cwd=take.HERE, env=env)
    time.sleep(3)
    try:
        take.mark("home")
        time.sleep(3)
        take.mark("demo")
        take.tap("시연 모드 · 예시 데이터", hold=4)
        take.tap("parked", hold=2)
        take.mark("refuel")
        take.tap("full", hold=10)
        take.mark("records")
        take.tap("기록", hold=5)
        time.sleep(2)
        take.mark("end")
        time.sleep(1)
    finally:
        screen.send_signal(signal.SIGTERM)
        screen.wait(timeout=90)
        json.dump(take.events, open(os.path.join(OUT, "events.json"), "w"), ensure_ascii=False, indent=1)


if __name__ == "__main__":
    main()
