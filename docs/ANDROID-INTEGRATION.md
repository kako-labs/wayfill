# Android 앱 연결 계약 · 현재 통합 브랜치

`integrate/podong-approved`에서 네이티브 UI는 `feature/assist`로 이동했다. 이전 `android/fuel-pilot-ui` 모듈 및 `onRequestWaypoint` 직접 콜백 예시는 11월 10일 UI 태그의 참고용이다. 최신 개발은 루트 Gradle 프로젝트를 사용한다.

## 빌드

JDK 17, Android SDK 35, minSdk 28, AGP 8.10.1, Kotlin 2.1.20, Compose BOM 2025.04.01 기준이다. 기기별 SDK 경로는 ANDROID_HOME/ANDROID_SDK_ROOT 또는 무시되는 local.properties로 지정한다.

```powershell
python scripts/dev.py doctor
python scripts/dev.py verify
.\gradlew.bat :app:testDebugUnitTest :app:assembleDebug --no-daemon --max-workers=1
```

## 앱 연결

Application은 Preferences DataStore와 기본 Unsupported 포트를 한 번 생성한다. AppHostViewModel은 AppCoordinator와 단일 AssistViewModel을 소유한다. MainActivity는 아래처럼 상태를 그리며 지도는 공급하지 않는다.

```kotlin
val state by host.assist.state.collectAsState()
FuelPilotUi(state = state, onIntent = host.assist::dispatch)
```

AppCoordinator는 복원 이후 구독되는 liveIntents에서 실제 포트를 읽고 판단 엔진/mapper 결과를 전달한다. 실제 정차·주유 신호는 VehicleEvents로 별도 공급한다. 화면은 SDK를 직접 호출하지 않는다. SelectStation → pending ID 생성 → AddWaypoint 효과 → NavigationRepository → 같은 ID/주유소의 NavigationCompleted 흐름이다.

내비 수집/요청 수명은 Activity의 일시적 Compose 수명이 아닌 ViewModel에 연결한다. 출발/경로 변경/주유로 pending이 지워지면 진행 중 coroutine을 취소한다. SDK 어댑터도 이 취소를 실제 SDK 작업/콜백 해제에 연결해야 한다.

## 신호와 저장

- 기본 실제 포트는 Unsupported다. 운영 경로에 시연 거리·연료·위치·주유소 또는 가짜 성공을 넣지 않는다.
- RouteResult.Available(null)만 목적지 없음을 뜻한다. 경로 미지원/미수신은 주변 추천으로 바꾸지 않는다.
- route.id는 같은 활성 경로의 거리 갱신 동안 유지하고 목적지/경로 생명주기 변경을 구분한다.
- vehicleEvents.motion은 실제 Motion.Parked/Moving/Unknown, refuels는 검증된 RefuelEvent를 발행한다. 이벤트 재전달에도 같은 ID를 쓴다.
- 읽기 5초와 내비 응답 10초 제한이 있다. 공급 어댑터가 Fresh/Stale/Unavailable/Unsupported를 정확히 판단한다.
- DataStore 복원이 끝난 뒤 입력을 구독한다. 저장 실패 시 새 인사/요청을 발행하지 않고 저장 오류 안내를 표시한다. 복원을 다시 시도하려면 앱을 재실행한다.
- 90일/256개 처리 ID와 모션 설정은 보존한다. celebration/단조 시계는 프로세스 재시작 후 복원하지 않는다.
- OS 애니메이션 배율 0은 모션 감소 신호로 연결한다. 모션을 줄여도 캐릭터와 메시지는 유지한다.

승인 원본은 고정 태그에 보존되어 있다. 최신 앱에는 `feature/assist`의 Kotlin 코드·아틀라스·프레임 좌표/표시 영역을 같은 버전으로 사용한다. app-debug.apk는 설치 가능한 앱이며 라이브러리 AAR과 구분한다.
