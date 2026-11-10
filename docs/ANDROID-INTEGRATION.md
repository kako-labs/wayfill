# Android 연결 계약

## 빌드 기준

이 전달본은 AGP 8.10.1, Kotlin 2.1.20, Compose BOM 2025.04.01, Java 17 타깃, compileSdk 35, minSdk 26으로 빌드합니다. 회사 앱의 버전에 맞춰 모듈 플러그인 선언과 의존성을 조정할 수 있습니다.

```powershell
cd android
# local.properties에 회사 노트북의 Android SDK 경로를 지정
.\gradlew.bat :fuel-pilot-ui:testDebugUnitTest :fuel-pilot-ui:assembleDebug
```

`local.properties`는 기기별 파일이라 Git/ZIP에 넣지 않습니다. Java/JDK는 Android Studio의 내장 JBR 또는 호환 JDK를 사용하세요.

## UI와 호스트의 책임

| UI 모듈이 제공 | 기존 앱이 제공 |
| --- | --- |
| 추천 팝업·주행 위젯·지나치기 전·완료·분석·설정 | 차량 연료/주행 가능 거리/정차 상태 |
| 뽀동이 포즈와 재생/중단 | 주유소 탐색·추천/도달 가능성 판단 |
| 상태 reducer, 중복 ID 억제 | 내비 경유지 추가 및 성공·실패 결과 |
| null 값의 확인 중 표시 | 검증된 주유 완료 이벤트와 저장/복원 |

## 배치 예시

```kotlin
// ViewModel이 FuelUiState를 소유하고, 아래 dispatch에서 reducer로 갱신한다.
// remember만으로 실제 앱 상태를 소유하면 재생성/재시작 시 중복 억제가 사라진다.
FuelPilotUi(
    state = uiState,
    route = routeUi,
    onEvent = viewModel::dispatch,
    onRequestWaypoint = { stationId ->
        // existingNavigator.addWaypoint(stationId)
        // 성공 응답: dispatch(FuelUiEvent.RouteAdded(stationId))
        // 실패 응답: dispatch(FuelUiEvent.RouteFailed(stationId))
    },
    modifier = Modifier.fillMaxSize(),
    mapContent = { /* 기존 앱에서 허용된 지도/표면. 임의 시스템 오버레이 아님. */ },
)
```

이 함수는 `dev.fuelpilot.ui` 패키지에 있습니다. 외부 호스트는 `FuelUiState`, `FuelUiEvent`, `FuelRouteUi`, `FuelStation`, `reduceFuelUi`를 사용합니다.

## 필수 이벤트

```kotlin
// 시작 상태는 Motion.Unknown. 실제 차량 신호로 갱신한다.
dispatch(FuelUiEvent.MotionChanged(Motion.Parked))
dispatch(FuelUiEvent.Show(Screen.Recommendation))

// 접근 중인 B를 추천 주유소, 다음 C를 nextAfterPassing으로 전달한다.
dispatch(FuelUiEvent.Show(Screen.Passing))

// 서버/영수증/검증된 연료 상승 등 기존 앱이 확인한 이벤트.
dispatch(FuelUiEvent.RefuelConfirmed(RefuelEvent(
    id = "unique-stable-id-from-host",
    liters = verifiedLitersOrNull,
    rangeKm = latestRangeOrNull,
    isFull = verifiedFullFlag,
    wasLow = verifiedPriorLowFlag,
)))

// 출발 즉시. 모션 타이머가 끝날 때까지 기다리지 않는다.
dispatch(FuelUiEvent.MotionChanged(Motion.Moving))
```

- 모든 reducer 갱신을 동일한 UI dispatcher/직렬 경로에서 처리하세요.
- `seenIds`, `characterEnabled`, `reduceMotion`을 호스트 저장 계층에서 보존하고 **이벤트 구독 전에** 복원하세요. 이벤트 ID는 재전달 때도 같아야 합니다. `System.currentTimeMillis()`를 수신 때마다 새로 만들면 중복 방지가 무력화됩니다.
- 복원 시 `celebration = null`로 시작해 이전 인사를 재생하지 마세요. 처음 수신할 때부터 이미 처리됐던 과거 이벤트인지 호스트가 구분해야 합니다.
- 같은 프로세스의 화면 재생성에서는 `Celebration.startedAtMs`를 그대로 보존하세요. `System.nanoTime()` 기반의 시작 시간에서 현재 포즈를 계산해 남은 구간만 재생하며, 기한이 지났으면 재생하지 않습니다. 프로세스/기기 재시작 뒤에는 이 타임스탬프를 재사용하지 않고 위 규칙대로 celebration을 비웁니다.
- moving/unknown 상태에서 받은 주유 이벤트도 소비된 ID로 남습니다. 다음 정차에서 뒤늦게 춤추지 않습니다.
- 접근성의 모션 감소 요청은 호스트가 `reduceMotion = true`로 전달합니다. 웹은 `prefers-reduced-motion`을 따릅니다.
- `liters`, `rangeKm`이 없으면 null을 전달하세요. 임의 기본값 42L/610km를 넣지 않습니다.
- `isFull`, `wasLow`는 실제로 확인된 경우에만 true입니다. 소량 기준 10L는 시연 기본값이므로 앱 정책과 다르면 `refuelVariant`에서 조정하세요.

## 경로 데이터

`FuelRouteUi.rangeKm`은 최신 주행 가능 거리, `destinationKm`은 현재 남은 실제 경로 거리입니다. `FuelStation.distanceKm`에는 주유소 진입 경로까지 포함한 실제 경로 거리를 넣으세요. `extraMinutes`에는 호스트가 재탐색한 추가 시간을 넣습니다.

`recommended`와 `stations` 순서는 기존 앱의 판단 로직이 정합니다. UI는 추천 엔진을 대체하지 않습니다. `remainingAtStationKm`도 호스트가 산출한 예상값이며 해당 주유소에 도착한 **주유 전** 기준입니다.

`Screen.Passing`에서는 `recommended=B`, `nextAfterPassing=C`를 줍니다. range와 C까지 거리가 모두 유효하고 부족할 때만 강한 부족 문구를 표시합니다. 데이터가 없으면 확인 중 표현으로 바뀝니다.

주유 후에는 `refuel`의 최신 확인 값만 보여주고 주유 전 분석을 재사용하지 않습니다. 새 판단 결과가 준비되면 호스트가 이전 `refuel` 확인 상태를 정리하고 새 routeUi를 전달하세요.

## AAR 방식

```kotlin
implementation(files("libs/fuel-pilot-ui-debug.aar"))
implementation(platform("androidx.compose:compose-bom:2025.04.01"))
implementation("androidx.compose.material3:material3")
implementation("androidx.compose.ui:ui")
implementation("androidx.compose.foundation:foundation")
```

앱의 Compose 플러그인/런타임과 맞춰야 합니다. 이 AAR은 서명된 앱/APK가 아니고 UI 라이브러리입니다.
