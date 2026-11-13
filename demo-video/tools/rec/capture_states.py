"""Captures WAYFILL screens the story take does not show, for the screen review page (tools/gallery).

usage: python3 capture_states.py <out_dir> [step ...]
Full-resolution screenshots (2560x1440 PNG) of the real app on the PLEOS emulator, then of its demo mode. The real
states are driven with the Virtual Console (gear, speed, range), the filming helper FilmNaviTest (destination) and
taps found by text. Only touches the AVD "pleos" on emulator-5590. Writes <step>.png and captions.json.

Steps run in this order (state carries over): real (analysis, records, settings, parked recommendation, driving with
WAYFILL in front, nearby mode, unsupported energy), fuel (clears the app's data to show the unset fuel), demo.
"""
import json
import os
import subprocess
import sys
import time

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import wayfill_take as take  # noqa: E402

OUT = os.path.abspath(sys.argv[1])
os.makedirs(OUT, exist_ok=True)
STEPS = sys.argv[2:] or ["real", "fuel", "demo"]
APP_PKG = "dev.fuelpilot"
captions = json.load(open(os.path.join(OUT, "captions.json"))) if os.path.exists(os.path.join(OUT, "captions.json")) else {}


def shot(name, note=""):
    path = os.path.join(OUT, f"{name}.png")
    with open(path, "wb") as f:
        f.write(subprocess.run(take.ADB + ["exec-out", "screencap", "-p"], capture_output=True, timeout=60).stdout)
    texts = [n.get("text") for n in take.nodes() if n.get("text")]
    captions[name] = {"note": note, "texts": texts[:60]}
    json.dump(captions, open(os.path.join(OUT, "captions.json"), "w"), ensure_ascii=False, indent=1)
    print("shot", name, "|", " · ".join(texts[:12])[:160], flush=True)


def console(path):
    take.console("PUT", path)


def destination(lat, lon, name):
    take.shell(f"am instrument -w --user 10 -e class dev.fuelpilot.FilmNaviTest#destination -e lat {lat} -e lon {lon} "
               f"-e name {name} dev.fuelpilot.test/androidx.test.runner.AndroidJUnitRunner", timeout=90)


def app_front(wait=10):
    take.shell(f"am start --user 10 -n {take.APP} >/dev/null 2>&1")
    time.sleep(wait)


def drive(gear, speed):
    if speed == 0:
        console("/vehicle/controls?speed=0&accelerator=0&brake=30&steering=0")
        time.sleep(1.5)
        console(f"/vehicle/gear?gear={gear}")
    else:
        console(f"/vehicle/gear?gear={gear}")
        console(f"/vehicle/controls?speed={speed}&accelerator=20&brake=0&steering=0")


def grant():
    for p in ("android.permission.ACCESS_FINE_LOCATION", "android.permission.ACCESS_COARSE_LOCATION",
              "android.permission.POST_NOTIFICATIONS", "android.car.permission.CAR_ENERGY", "pleos.car.permission.FUSED_LOCATION"):
        take.shell(f"pm grant --user 10 {APP_PKG} {p}")


def real():
    # From the end of the story take: parked, guided via 미래주유소, monitoring on, range 70.
    app_front(8)
    take.tap("주유 안내", hold=4)
    shot("c01-analysis-on-route", "주유 안내 탭 · 경유지 안내 중")
    take.tap("기록", hold=3)
    shot("c02-records-empty", "기록 탭 · 실제 앱에는 아직 확인된 주유가 없음")
    take.tap("설정", hold=3)
    shot("c03-settings", "설정 · 판단 여유거리, 연료 종류, 캐릭터 움직임")
    take.tap("홈", hold=2)

    # A new route without the waypoint, range 70, parked: the recommendation on the home.
    console("/vehicle/odometer?remaining_range_m=70000")
    destination(37.2664, 127.0001, "수원역")
    app_front(14)
    shot("c04-home-recommend", "정차 중 · 주행 가능 70km · 목적지 23km · 주유 추천")
    take.tap("주유 안내", hold=4)
    shot("c05-analysis-recommend", "주유 안내 탭 · 추천 이유와 경로 위 주유소")
    take.tap("홈", hold=2)

    # Driving with WAYFILL in front: the compact driving surface.
    drive("Drive", 60)
    time.sleep(8)
    shot("c06-driving-recommend", "주행 중 WAYFILL을 앞에 둔 화면 · 추천")
    console("/vehicle/odometer?remaining_range_m=80000")
    time.sleep(10)
    shot("c07-driving-noaction", "주행 중 · 주유가 필요 없을 때")
    drive("Park", 0)
    time.sleep(6)

    # Nearby mode: end the guidance in the navigation, then WAYFILL looks around the car.
    take.press(*take.DOCK_NAV, "내비게이션", 3)
    take.tap("안내 종료", hold=3)
    console("/vehicle/odometer?remaining_range_m=60000")
    app_front(16)
    shot("c08-home-nearby", "목적지 없음 · 주변 주유소 안내(주행 가능 60km)")

    # No vehicle energy permission: the app says it cannot judge instead of inventing a range.
    take.shell(f"pm revoke --user 10 {APP_PKG} android.car.permission.CAR_ENERGY")
    app_front(12)
    shot("c09-home-energy-missing", "차량 주행 가능 거리를 읽을 수 없을 때")
    grant()


def fuel():
    # A fresh app on an electric car: no fuel type yet.
    take.shell(f"am force-stop --user 10 {APP_PKG}")
    take.shell(f"pm clear --user 10 {APP_PKG}")
    grant()
    destination(37.2664, 127.0001, "수원역")
    app_front(14)
    shot("c10-home-fuel-unknown", "차량이 연료 종류를 알려주지 않음 · 처음 실행")
    take.tap("설정", hold=3)
    shot("c11-settings-fuel-unset", "설정 · 연료를 아직 고르지 않음")
    take.tap("휘발유", hold=3)
    shot("c12-settings-fuel-set", "설정 · 휘발유 선택")
    take.tap("홈", hold=12)
    shot("c13-home-after-fuel", "연료를 고른 뒤의 홈")


def demo():
    # The demo entry sits on the status home, shown when no stop is needed; a recommendation home has no such button.
    console("/vehicle/odometer?remaining_range_m=150000")
    app_front(4)
    take.tap("홈", hold=10)
    if not take.tap("시연 모드 · 예시 데이터", hold=5):
        sys.exit("demo entry not found")
    take.tap("parked", hold=2)
    for scenario in ("recommendation", "passing", "noaction", "nearby", "stale", "unsupported", "unavailable"):
        take.tap(scenario, hold=3)
        shot(f"d-parked-{scenario}", f"시연 모드 · 정차 · {scenario}")
    take.tap("moving", hold=2)
    for scenario in ("recommendation", "passing", "noaction"):
        take.tap(scenario, hold=3)
        shot(f"d-moving-{scenario}", f"시연 모드 · 주행 · {scenario}")
    take.tap("parked", hold=2)
    take.tap("recommendation", hold=3)
    take.tap("navfailure", hold=1)
    take.tap("바로 안내", hold=4)
    shot("d-nav-failure", "시연 모드 · 내비가 경유지를 받지 못했을 때")
    take.tap("홈으로", hold=2)
    take.tap("recommendation", hold=3)
    take.tap("navsuccess", hold=1)
    take.tap("바로 안내", hold=4)
    shot("d-nav-success", "시연 모드 · 내비가 경유지를 받았을 때")
    for kind in ("small", "full", "recovery"):
        take.tap("parked", hold=1)
        take.tap(kind, hold=4)
        shot(f"d-refuel-{kind}", f"시연 모드 · 주유 확인 · {kind}")
        take.tap("확인했어요", hold=2)
    take.tap("기록", hold=3)
    shot("d-records", "시연 모드 · 주유 기록 목록")


def main():
    if take.adb("emu", "avd", "name").split()[0] != "pleos":
        sys.exit("not the pleos AVD")
    beat = take.Heartbeat()
    beat.start()
    try:
        for step in STEPS:
            {"real": real, "fuel": fuel, "demo": demo}[step]()
    finally:
        beat.stop_event.set()
        json.dump(captions, open(os.path.join(OUT, "captions.json"), "w"), ensure_ascii=False, indent=1)


if __name__ == "__main__":
    main()
