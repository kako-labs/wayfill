# WAYFILL

주유하기 좋은 마지막 기회를 놓치기 전에 알려 주는 Pleos Connect 차량용 앱입니다.

- **출발 전:** 차량의 주행 가능 거리와 내비 목적지까지의 거리를 비교해, 도착한 뒤에도 정한 여유(기본 50km)가 남는지 판단합니다.
- **주행 중:** 여유를 지킬 수 없게 되기 전에, 지금 경로에서 여유를 지키며 들를 수 있는 가장 먼 주유소를 내비 위 알림 카드와 차량 음성으로 묻습니다.
- **바로 안내:** 그 주유소를 Pleos 내비의 경유지로 넣어 달라고 요청하고, 내비가 받아들였는지 경유지 목록으로 확인합니다.
- **주유 후:** 차량 연료량이 늘면 새 연료 상태로 다시 판단하고 기록에 남깁니다.

## 심사용 실행 안내

### APK

`apk/KA&KO_WAYFILL_release.apk`

- release 빌드, 서명됨, 버전 1.0.0(versionCode 3)
- 패키지 `dev.fuelpilot`(개발 초기 이름을 그대로 둡니다)
- 주유소·경로 서버 `https://wayfill.quickkit.net`이 들어 있습니다. API 키는 서버에만 있고 앱에는 없습니다.

### 테스트 환경

| 항목 | 값 |
| --- | --- |
| 에뮬레이터 | Pleos Connect 에뮬레이터 v2.1.0 (Android 14, API 34, arm64, user build) |
| 화면 규격 | 2560×1440, 160dpi |
| 차량 입력 | 에뮬레이터의 Virtual Console(기어·속도·주행 가능 거리) |
| 인터넷 | 필요합니다. 주유소 위치·가격(오피넷)과 경로·우회 시간(카카오모빌리티 길찾기)을 WAYFILL 서버를 거쳐 받습니다 |
| API 키·서버 | 심사 환경에서 설정할 것은 없습니다. 서버 상태: `https://wayfill.quickkit.net/v1/health` |
| 실차 | 확인하지 않았습니다. 모든 결과는 에뮬레이터 기준입니다 |

### 설치와 실행

1. 에뮬레이터를 켜고 APK를 설치합니다: `adb install "apk/KA&KO_WAYFILL_release.apk"` (파일 이름의 `&` 때문에 따옴표가 필요합니다). debug 빌드가 설치돼 있으면 서명이 달라 먼저 `adb uninstall dev.fuelpilot`를 합니다.
2. 하단 바의 WAYFILL 아이콘으로 앱을 엽니다.
3. 홈의 **모니터링 시작**을 누르고 위치, 자동차 정보, 알림 권한을 허용합니다.
4. 에뮬레이터 차량은 전기차라 연료 종류를 알려 주지 않습니다. **설정 › 연료 종류**에서 휘발유 등을 고릅니다.
5. 내비에서 목적지를 정합니다(예: 판교에서 수원역 23km). 목적지가 없으면 현재 위치 주변에서 가까운 주유소를 고릅니다.
6. Virtual Console에서 주행 가능 거리를 낮춥니다(예: 80km → 70km). 목적지에서 판단 여유를 남길 수 없게 되면 홈에 추천 주유소가 나옵니다.
7. 기어를 D로 두고 속도를 주면 내비를 앞에 둔 채 판단이 이어지고, 추천 시점에 내비 위 알림 카드와 차량 음성이 묻습니다. **바로 안내**를 누르면 그 주유소가 내비 경유지로 들어갑니다.
8. 정차하면 홈에 "…에 들렀다 가는 경로로 안내 중이에요"가 보이고, 같은 주유소를 다시 권하지 않습니다.

### 실제 연결과 시연 데이터의 범위

| 기능 | 이 APK에서 |
| --- | --- |
| 주행 가능 거리, 기어, 속도 | Pleos 차량 신호 |
| 내비 목적지, 남은 거리, 경유지 추가와 확인 | Pleos 내비 연동 |
| 알림 카드, 음성 안내 | 시스템 알림(HUN), Pleos TTS |
| 주유소 위치·가격, 경로 우회 시간 | 오피넷과 카카오모빌리티의 실제 데이터(WAYFILL 서버) |
| 연료 종류 | 차량 값을 먼저 쓰고, 없으면 설정에서 고른 값 |
| 주유 확인, 주유 기록 | 차량 연료량 신호가 필요합니다. 전기차 에뮬레이터에서는 이 신호가 없어 나오지 않습니다 |

시연 영상의 주유 확인과 기록 장면은 debug 빌드의 시연 모드(예시 데이터)로 찍었고, 영상에 그렇게 표시했습니다. release APK에는 시연 모드가 없습니다. 영상 링크는 접수 메일에 적습니다.

### 주요 문서

- [시연 영상 제작](demo-video/README.md): 촬영 전략, 녹화·편집 도구, 공개 사항
- [서버 배포](docs/gateway-deploy.md): 게이트웨이, 키 관리, 요청 제한
- [제출 문서](docs/submission/README.md)

### 소스에서 빌드

- JDK 17과 Android SDK가 필요합니다: `python3 scripts/dev.py doctor`, 전체 검사 `python3 scripts/dev.py verify`
- 서버 주소는 `gradle.properties`의 `stationGatewayOrigin`입니다.
- release 서명 값은 Git이 무시하는 `local.properties`의 `release.storeFile`, `release.storePassword`, `release.keyAlias`, `release.keyPassword`에서 읽습니다. 없으면 `./gradlew :app:assembleRelease`가 서명 없이 빌드합니다.

## 개발 기록

아래는 개발 중 남긴 인계 기록입니다. 날짜가 지난 브랜치·Release 안내는 그때 기준입니다.

2026-11-13 제품 인계: 사용자가 확정한 석양 홈과 도로 띠 뽀동이를 웹·Android 홈/주유 안내/주유 인사/기록/설정에 적용했다. 추천·소량·충분한 주유·저연료 회복의 32포즈 모션과 정차·주행 구분, 기록 저장·삭제, 내비 응답과 알림 수명을 연결했다.

- [지금의 실제 앱 화면 모음](docs/screen-gallery.html)
- [PLEOS 상황 흐름 시뮬레이터](web/pleos-scenario-simulator.html) · [직접 조작하는 앱 미리보기](web/companion-preview.html) · [새 캐릭터 모션 검토](web/sunset-motion-review.html)
- [현재 구현·검증·실제 연결이 남은 부분](docs/PRE-PLEOS-READINESS-2026-11-13.md)
- [다른 노트북에서 받는 순서](docs/CROSS-DEVICE-2026-11-13.md)

작업 브랜치: `feature/prepleos-hardening-20261113`. 팀원용 PLEOS 연결 기준이며 [팀원 인계 문서](docs/TEAMMATE-PLEOS-HANDOFF-2026-11-13.md)를 먼저 읽는다. [인계 Release](https://github.com/kako-labs/wayfill/releases/tag/wayfill-prepleos-handoff-2026-11-13)는 이 브랜치의 검증된 커밋을 고정한다. 기본 `main`과 과거 소프트 선셋 Release에는 이번 32포즈 모션·시스템 HUN 음성 계약·검증된 내비 타깃 변경이 없다. 실제 PLEOS/실차 검증, 운영 주유 이벤트와 공급자 좌표/도로 거리/연료 타입 연결은 남아 있다. SDK가 없으면 실제 빌드가 Unknown/Unsupported로 유지되는 것이 정상이다.

## 이전 인계 기록

아래 11월 10~12일 링크는 과거 기준이다. 현재 상태는 위 11월 13일 문서를 우선한다.


**최신 화면 재설계:** [정차 홈·작은 창·연결 대기·알림 시안](web/companion-preview.html) · [Motion Care 비교와 변경 근거](docs/REDESIGN-2026-11-12.md). 실제 OS 알림 위치는 앱이 지정하지 않습니다.

**내비 옆 작은 창·백그라운드 안내 후속:** [제품 기준과 연결 지점](docs/ADAPTIVE-COMPANION.md) · [실행한 검증과 미확인 항목](docs/verification-adaptive-2026-11-12.md) · [오프라인 화면 비교](web/companion-preview.html). 웹 알림은 검토용 시안이며 실제 PLEOS 알림 캡처가 아닙니다.

**다른 노트북에서 이어갈 최신 전체 인계본:** [2026-11-11 Release](https://github.com/kako-labs/wayfill/releases/tag/fuel-pilot-handoff-2026-11-11) · [기기 간 인계 문서](docs/CROSS-DEVICE-2026-11-11.md). 개발 브랜치는 `integrate/podong-approved`이며 기본 `main`과 구분합니다.

연료가 부족해진 뒤가 아니라, 현재 경로에서 주유하기 좋은 마지막 안전 기회를 놓치기 전에 알려 주는 Pleos 차량용 주유 보조 앱입니다.

현재 제품 기준선은 MVP 단계입니다. 실제 Pleos Vehicle SDK, NaviHelper, 주유소 POI 공급자 연결은 차량·SDK 환경에서 별도로 검증해야 하며, 실제 연동이 없을 때 운영 빌드가 합성 데이터로 대체해서는 안 됩니다.

## 제품 개발 이어서 하기

1. `git clone https://github.com/kako-labs/wayfill.git`
2. 인계 브랜치 `feature/prepleos-hardening-20261113`로 전환합니다.
3. [최신 이어가기 안내](CONTINUE-HERE.md)와 [제품 인계 문서](docs/handoff.md)를 먼저 읽습니다.
4. `python scripts/dev.py doctor`로 JDK 17과 Android SDK를 확인합니다.
5. 핵심 엔진 테스트: `./gradlew :core:model:test --no-daemon --max-workers=1`

현재 PLEOS 연결 순서는 [팀원 인계 문서](docs/TEAMMATE-PLEOS-HANDOFF-2026-11-13.md)를 우선하고 과거 설계와 구현 이력은 [`docs/superpowers/specs`](docs/superpowers/specs)와 [`docs/superpowers/plans`](docs/superpowers/plans)에 보존합니다.

11월 11일 제품 준비 변경에는 홈·정차 설정, 공유 런타임/모니터링 서비스, 목적지/경유지 요청 구분, debug 전용 네이티브 시연, 주유소 서버 경계와 Android 화면 검사/CI가 포함됩니다. [검증과 실제 화면](docs/product-readiness-2026-11-11.md), [다른 기기용 소스 전달](docs/submission/portable-source.md), [제출 문서](docs/submission/README.md)를 확인하세요. SDK가 없는 기본 앱은 미지원 상태를 표시하며, debug 앱 홈에서만 ‘시연 모드 · 예시 데이터’에 들어갑니다. release에는 시연 코드가 없습니다.

## 승인된 뽀동이 UI 기준선

주유가 필요한 이유를 먼저 설명하고, 메시지마다 뽀동이가 함께하며 정차·주유 후에는 귀엽게 반응하는 승인 UI 전달본을 함께 보존합니다.

- [승인 버전 시작 안내](START-HERE.md)
- [UI 세션 인계문](docs/UI-SESSION-HANDOFF.md)
- [앱 개발 세션 인계문](docs/APP-SESSION-HANDOFF.md)
- [사용자 승인 기준과 파일 매핑](docs/APPROVED-BASELINE.md)
- [Android 연결 계약](docs/ANDROID-INTEGRATION.md)

고정 태그와 [승인 버전 다운로드](https://github.com/kako-labs/wayfill/releases/tag/podong-ui-approved-2026-11-10)는 `podong-ui-approved-2026-11-10`입니다. 로컬 서버 없이 다음 HTML 파일을 브라우저에서 열 수 있습니다.

- `web/animation-lab.html`: 5개 동작·72프레임 애니메이션 비교
- `web/index.html`: 설치 없이 실행하는 오프라인 인터랙티브 데모 11개 상황
- `feature/assist`: 상태 reducer와 원본 캐릭터 리소스를 포함한 통합 Compose 라이브러리
- `feature/assist/build/outputs/aar/assist-debug.aar`: 검증 빌드에서 생성되는 Android UI 라이브러리
- `reference/claude-export.html`: 사용자가 지정한 뽀동이 원본 모션과 디자인 자료

UI 기준선 검증: `npm.cmd test`, `npm.cmd run verify`, `.\\gradlew.bat :feature:assist:testDebugUnitTest :feature:assist:assembleDebug --no-daemon --max-workers=1 --console=plain`.

## 현재 앱 실행

승인 UI를 앱 시작 화면에 연결했습니다. SDK 없는 환경에서는 뽀동이와 명시적인 미지원 안내를 표시합니다. 실제 포트 연결 지점은 AppDependencies이며 화면에 시연 데이터를 주입하지 않습니다. [최신 검증 기록](docs/verification-2026-11-11.md)을 확인하세요.

```powershell
.\gradlew.bat :app:assembleDebug --no-daemon --max-workers=1
```

설치할 APK: `app/build/outputs/apk/debug/app-debug.apk`. 실제 Pleos 차량 연동 검증은 별도입니다.
