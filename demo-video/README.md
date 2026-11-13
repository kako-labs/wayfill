# WAYFILL 시연 영상

공모전 제출용 시연 영상과 화면 리뷰 페이지를 만드는 프로젝트입니다. 실제 앱을 Pleos Connect 에뮬레이터에서 한 번에 녹화하고(Remotion으로 편집), 연료 신호가 필요한 주유 확인만 앱의 시연 모드로 찍습니다.

`docs/WAYFILL-VIDEO-PRODUCTION-PLAN.md`(2분 30초 사전 기획안)는 실기 녹화 전 계획입니다. 에뮬레이터에서 실제로 찍은 영상은 이 문서를 따릅니다.

## 결과물

| 파일 | 길이 | 내용 |
| --- | --- | --- |
| `out/wayfill-story.mp4` | 2:11 | 자막판. 배경음악 없이 차량 음성과 내비 음성만 |
| `out/wayfill-story-tts.mp4` | 2:15 | 내레이션판. 자막과 같은 문장을 ElevenLabs 음성으로 |

둘 다 1920×1080, 30fps입니다. 렌더 결과와 720p 미리보기(crf 27), 원본 녹화본, 화면 캡처는 저장소 밖 `~/Documents_LOCAL/wayfill-video/`에 둡니다(`final/`, `render-sources/takes/`, `gallery/`).

## 전략

원칙은 사용자 여정 순서, 화면 고정, 1배속, 원인과 결과를 한 화면에, 자막 한 문장과 장면 표시줄, 확대·반짝임·배속·배경음악 없음, 끝에 공개 사항입니다.

연출·HMI·심사위원 세 관점으로 검토해 공통으로 남긴 것은 세 가지입니다.

1. 정차 중 홈에서 "지금은 주유가 필요 없다"는 판단을 먼저 보여 준다.
2. 주행 중 내비 위 알림 카드(HUN)와 차량 음성이 한 번 묻는다.
3. 바로 안내가 실제 Pleos 내비에 경유지로 들어가는 것을 보여 준다.

이야기는 판교(에뮬레이터 고정 위치) → 수원역 23km, 판단 여유 50km입니다. 주행 가능 80km에서는 주유가 필요 없고, 주행 중 70km까지 내려가면 미래주유소(17.7km 앞, 경로 +4분)를 권합니다.

| 장면 | 자막판 시작 | 보여 주는 것 |
| --- | --- | --- |
| 출발 전 | 0:03 | 내비 안내 중 WAYFILL 열기 → 주유 필요 없음 → 모니터링 시작 |
| 주행 | 0:27 | 내비를 앞에 두고 D, 60km/h → 주행 가능 80→70km → HUN과 음성 |
| 경로 반영 | 1:01 | 바로 안내 → 내비가 경유지 1개 포함 경로 → 주행 중 확인 → 정차 후 "안내 중" |
| 주유 후 | 1:39 | 시연 모드: 가득 주유 확인 → 기록 |

### 에뮬레이터 제약과 공개

- 에뮬레이터 내비의 위치는 GPS·속도·재실행으로도 움직이지 않고 시뮬레이션 옵션도 없습니다. 그래서 줄어드는 주행 가능 거리를 Virtual Console로 넣고, 영상에 입력 라벨을 표시합니다.
- 전기차 에뮬레이터에는 연료량 신호가 없어 주유 확인은 시연 모드 예시 데이터입니다. 시연 모드 화면 위쪽 개발용 버튼 줄은 "시연 모드 · 예시 데이터" 라벨로 가립니다.
- 엔딩에서 두 가지를 모두 밝힙니다.

## 준비

에뮬레이터는 AVD `pleos`를 **emulator-5590, gRPC 8592**로 띄웁니다. 같은 기기에서 다른 프로젝트가 emulator-5570(gRPC 8590, `tcp:8088` 포워드)을 쓰므로 그 포트와 기기는 건드리지 않고, `adb kill-server`도 쓰지 않습니다.

```sh
emulator -avd pleos -port 5590 -read-only -no-snapshot -no-window -no-boot-anim -gpu host -memory 6144 -grpc 8592
adb -s emulator-5590 forward tcp:18088 tcp:8088   # Virtual Console
adb -s emulator-5590 shell pm disable-user --user 10 <other demo app>   # optional: keep only WAYFILL in the dock
```

Virtual Console은 3초 동안 클라이언트가 없으면 입력을 버립니다. 녹화 스크립트가 1초마다 `/browser/session`에 신호를 보냅니다.

목적지는 앱의 실제 내비 포트로 넣습니다. 촬영 도우미는 앱 테스트에 넣지 않고, 촬영할 때만 `androidTest`에 잠시 두고 빌드해 설치합니다.

```kotlin
// FilmNaviTest.kt (androidTest, filming only)
class FilmNaviTest {
    @Test fun destination(): Unit = runBlocking {
        val args = InstrumentationRegistry.getArguments()
        val port = PleosNaviHelperPort(InstrumentationRegistry.getInstrumentation().targetContext)
        val target = NavigationTarget("film-destination", args.getString("lat")!!.toDouble(), args.getString("lon")!!.toDouble(),
            args.getString("name")!!, NavigationTargetProvenance.VERIFIED_PROVIDER_WGS84)
        Log.i("WF_FILM", "setDestination=${port.setDestination(target)} route=${port.readRoute()}")
    }
}
```

```sh
adb -s emulator-5590 shell am instrument -w --user 10 -e class dev.fuelpilot.FilmNaviTest#destination \
  -e lat 37.2664 -e lon 127.0001 -e name 수원역 dev.fuelpilot.test/androidx.test.runner.AndroidJUnitRunner
```

## 촬영

```sh
cd tools/rec && npm install
python3 wayfill_take.py <out>/main     # one take of the real app: screen 1920x1080, car audio, logcat, events, clock
python3 demo_take.py <out>/demo        # demo mode: refuel and records (start on the real home, parked)
python3 prep_wayfill.py <out>/main <out>/demo   # run from demo-video/: public/rec/*, src/data/{main,demo}.json
```

`wayfill_take.py`의 탭 좌표(하단 바 아이콘, HUN 버튼)는 2560×1440 화면에서 잰 값입니다. 녹화 뒤 `events.json`의 `card`에 HUN 문구가, `waypoint`에 내비 반영 여부가 남습니다. 소리는 패킷이 늦게 도착하므로 `prep_wayfill.py`가 음성 엔진 로그와 첫 큰 소리 패킷으로 지연을 재서 맞춥니다.

## 편집과 내레이션

```sh
npm install --include=dev        # NODE_ENV=production shells skip devDependencies otherwise
npm run studio                   # preview
npm run render && npm run render-tts
```

장면표는 `src/story/scenes.ts`, 자막·강조 상자·입력 라벨은 `src/story/Story.tsx`에 있습니다. Remotion은 시스템 Chrome을 씁니다(`remotion.config.ts`). 폰트는 `public/fonts/NotoSansKR-VF.ttf`를 넣어 둡니다.

내레이션 문장은 `src/story/narration-lines.json`입니다. 숫자는 읽는 대로 적습니다(팔십 킬로미터).

```sh
python3 tools/tts/narrate.py           # ELEVENLABS_API_KEY, ELEVENLABS_VOICE_ID; only changed lines are spoken
./node_modules/.bin/esbuild tools/tts/placement.ts --bundle --platform=node --log-level=error | node
python3 tools/tts/check.py --video out/wayfill-story-tts.mp4   # whisper medium
```

`placement.ts`는 각 문장이 자막 안에서 끝나는지, 차량·내비 음성과 겹치지 않는지 봅니다. `home.0`은 일부러 두 자막에 걸칩니다.

## 검증

- 1초 또는 3초 간격 프레임 시트로 장면 순서, 자막, 숫자(80→70km, 17.7km, +4분, 23km)를 화면과 대조합니다.
- 내레이션판은 whisper로 18문장이 모두 끝까지 들리는지 봅니다(2026-11-14: 18/18).
- 720p 미리보기: `ffmpeg -i out/wayfill-story.mp4 -vf scale=1280:720 -c:v libx264 -crf 27 -preset slow -c:a aac -b:a 96k -movflags +faststart wayfill-story-720p.mp4`

## 화면 리뷰 페이지

영상의 장면과 영상에 없는 상태를 한 페이지에 모읍니다. 각 화면에 상황, 할 수 있는 것, 이번에 바뀐 점, 살펴볼 점을 적습니다(`tools/gallery/shots.py`).

```sh
python3 tools/rec/capture_states.py <raw> real fuel demo   # 2560x1440 captures + captions.json (fuel clears the app data)
python3 tools/gallery/build.py <raw> <final> <site>        # img/*.jpg, video/*, index.html
```

`capture_states.py`의 `demo` 단계는 홈 탭에서 "시연 모드 · 예시 데이터" 버튼이 보여야 시작합니다(주행 가능 150km, 정차, 추천 없음).

2026-11-14 촬영에서 찾은 살펴볼 점:

1. 앱을 연 직후 약 3초 동안 "차량과 경로 정보를 확인할 수 없어요"가 보입니다. 읽는 중이라는 문구가 맞습니다.
2. 목적지가 없을 때 홈이 "가는 길에 주유하고 가요 · 현재 경로의 주유 추천 · 목적지까지 확인 중"을 씁니다.
3. 주행 중 주유가 필요 없을 때 보조 문구가 "신호가 확인되면 현재 경로에 맞춰 안내할게요"입니다.
4. 시연 모드에서 5L 주유 뒤에도 확인 후 주행 가능이 550km입니다.
