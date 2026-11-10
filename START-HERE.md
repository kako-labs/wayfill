# 내일 회사 노트북에서 시작하기

FUEL PILOT의 승인된 문구와 뽀동이 모션을 화면과 코드로 묶었습니다.

## 1. 받기

```powershell
git clone --branch ui/podong-delivery-20261110 https://github.com/kako-labs/fuel-pilot.git
```

이미 이 저장소가 있다면 현재 작업을 먼저 보존하고 브랜치를 전환하거나, 다른 폴더에 위 명령으로 새로 받으세요. 이 브랜치는 기존 앱 소스에 강제로 덮어쓰는 패치가 아닙니다.

## 2. 화면부터 보기

`web/animation-lab.html`에서 새 동작 5종을 먼저 보고, `web/index.html`을 Chrome/Edge에서 더블클릭해 전체 UI를 여세요. 인터넷·Node·Python 없이 동작합니다.

상단 버튼은 실제 앱 메뉴가 아니라 시연 상황 선택 도구입니다.

| 시연 상황 | 화면/동작 |
| --- | --- |
| ① 주유 추천 | 목적지까지 연료가 부족한 이유 → 주유소 A → 두 버튼 |
| ② 주행 중 위젯 | 작은 뽀동이와 차분한 다음 주유소 안내 |
| ③ 지나치기 전 | B를 지나면 C까지 연료 부족 예상 |
| ④ 경유지 추가 | 내비 응답 후 완료 확인 |
| ⑤ 주유 완료 | 정차 중 2초의 뽀동이 인사 |
| 소량 / 가득 / 바닥에서 가득 | 1.6초 / 2.4초 / 2.4초의 인사와 다른 말풍선 |
| 장거리 출발 | 정차 중 여행 준비 말풍선, 출발하면 위젯으로 |
| ⑥ 경로 분석 | 주유 필요 이유·주유소 비교 |
| ⑦ 설정 | 정차 중 귀여운 움직임 켜기/끄기, 뽀동이 표시는 유지 |

뽀동이는 메시지가 보이는 동안 계속 곁에 있습니다. 주유 완료 상황을 고른 직후 `출발하기`를 누르면 큰 동작을 멈추고 작고 차분한 모습으로 바뀝니다. `정차하기` 후 `같은 이벤트 다시 전달`을 눌러도 재생되지 않아야 합니다. 새 인사를 보려면 주유 완료 상황 버튼을 다시 눌러 새 이벤트를 만드세요.

## 3. Android Compose 앱에 적용

`android/fuel-pilot-ui`는 Android 라이브러리 모듈입니다. 앱 화면, 캐릭터 PNG 리소스, 상태 reducer를 포함합니다. 기존 앱의 차량/내비 SDK와 직접 연결하는 부분은 콜백으로 남겼습니다.

모듈 복사 방식이 가장 단순합니다.

1. 회사 앱의 새 작업 브랜치에서 `android/fuel-pilot-ui` 폴더를 프로젝트에 복사합니다.
2. `settings.gradle.kts`에 `include(":fuel-pilot-ui")`를 추가합니다.
3. 앱 모듈에 `implementation(project(":fuel-pilot-ui"))`를 추가합니다.
4. 회사 앱이 version catalog로 플러그인 버전을 관리한다면 모듈의 `plugins` 선언을 기존 방식에 맞추세요. Kotlin Compose 플러그인이 필요합니다.
5. [연결 계약](docs/ANDROID-INTEGRATION.md)의 예시처럼 `FuelPilotUi`를 배치하고 이벤트와 콜백을 연결합니다.

원하는 경우 빌드된 `release-artifacts/fuel-pilot-ui-debug.aar`도 쓸 수 있습니다. AAR 단독으로는 Compose 의존성을 가져오지 않으므로 연결 문서의 의존성을 앱에 추가해야 합니다. 소스 모듈 복사를 권장합니다.

## 4. 앱 개발 세션에 붙여 넣을 인계문

> 이 저장소의 START-HERE.md와 docs/ANDROID-INTEGRATION.md, docs/PODONG-BEHAVIOR.md를 먼저 읽어줘. web/index.html은 상태별 기준 UI이고 android/fuel-pilot-ui는 빌드 확인된 Compose 라이브러리야. 기존 앱의 차량·주유소 추천·내비 경유지 로직을 보존하면서 UI만 연결해줘. FuelUiState의 motion은 실제 차량 상태를 반영하고 기본 Unknown을 유지해. 주유 완료는 검증된 이벤트 ID로 전달하고 seenIds와 설정을 ViewModel/저장소에 보존해 중복 인사를 막아줘. onRequestWaypoint는 요청일 뿐이므로 내비 SDK 성공 응답에서만 RouteAdded를 보내줘. 내비 지도 위 표시 권한을 확인하기 전에는 순정 지도 오버레이가 된다고 가정하지 말고 앱 내부 표면부터 연결해줘. 화면 수치는 예시이므로 하드코딩하지 마. 작업 후 앱 빌드와 주유 완료/중복 이벤트/출발 중단/데이터 미수신을 확인하고, 작업 브랜치와 인계 내용을 GitHub에 보존해줘.

## 확인 범위

- 웹은 독립 실행되는 UI 데모입니다. 지도는 시연용 그림이고 차량·유가·주유소 API를 호출하지 않습니다.
- Android 라이브러리는 빌드와 상태 단위 테스트로 검증했습니다. 회사 앱과의 통합이나 실제 차량 실행까지 끝난 것은 아닙니다.
- GPT로 새로 그린 72프레임을 웹과 Android에서 실제 프레임 애니메이션으로 재생합니다. Rive/Lottie 리깅 파일은 아닙니다.
