# WAYFILL MVP Implementation Plan

> For agentic workers: REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development or superpowers:executing-plans. Steps use checkbox syntax for tracking.

**Goal:** 경로 기반 마지막 안전 주유 기회와 목적지 없는 주변 추천을 계산하고 지도 없는 UI에서 한 줄 이유와 내비 전달 흐름을 시연하는 Android MVP를 만든다.

**Architecture:** 순수 Kotlin 판단 엔진을 중심으로 에너지·위치·POI·내비 Repository를 분리한다. demo 빌드는 합성 adapter로 전체 흐름을 제공하고 실제 adapter는 지원·권한·실측 상태를 Unavailable로 보존한다.

**Tech Stack:** Kotlin, Android 35, Jetpack Compose, Coroutines, StateFlow, Navigation Compose, JUnit, Gradle 8.12, JDK 17

**Spec:** docs/superpowers/specs/2026-11-10-fuel-pilot-mvp-design.md

## Global Constraints

- 패키지는 dev.fuelpilot이고 사용자 UI는 한국어다.
- 앱 내부 지도·지도 SDK·경로선·턴 안내를 만들지 않는다.
- 합성 데이터는 demo 빌드와 화면에서 명시하고 실제 실패의 fallback으로 사용하지 않는다.
- 기본 최소 잔여거리는 50km이며 정차 중 20~100km를 10km 단위로 설정한다.
- 가격은 MVP에서 제외한다.
- 내비 전달은 사용자 한 번 확인과 성공 콜백 뒤에만 완료로 표시한다.

## Review Focus

- 목적지가 바뀌면 이전 후보를 폐기하고 다시 계산한다.
- 오래된 에너지·위치·영업 정보를 추천 근거로 사용하지 않는다.
- 경로 계산이 없으면 우회 시간과 경로 이탈 근거를 표시하지 않는다.
- 경유지가 2개면 기존 경로를 바꾸지 않고 미지원 상태를 표시한다.
- 연속 탭과 늦은 콜백이 중복 경유지를 만들지 않는다.

## Task 1: 프로젝트 골격

**Files:** root Gradle, app, core/model, data 모듈 4개, feature/assist, scripts/dev.py, AGENTS.md, docs/architecture.md

- [ ] 허용 모듈 edge를 검사하는 실패 테스트를 먼저 만든다.
- [ ] 최소 Android 모듈과 verifyAll을 구현한다.
- [ ] JDK 17과 Android 35에서 빈 앱 빌드와 테스트를 통과시킨다.
- [ ] chore: Android 모듈 골격 추가로 커밋한다.

## Task 2: 마지막 안전 주유 판단

**Files:** FuelModels.kt, FuelDecisionEngine.kt, FuelDecisionEngineTest.kt

- [ ] 충분한 도착 여유가 NoAction인 실패 테스트를 작성한다.
- [ ] 다음 후보가 안전하지 않을 때 현재 후보가 LastSafeStop인 실패 테스트를 작성한다.
- [ ] 목적지가 없을 때 영업·유종·거리로 추천하는 실패 테스트를 작성한다.
- [ ] stale, 거리 전용, 후보 없음 테스트를 작성한다.
- [ ] FuelDecisionEngine.evaluate를 최소 구현하고 전체 테스트를 통과시킨다.
- [ ] feat: 마지막 안전 주유 판단 추가로 커밋한다.

## Task 3: 한 줄 추천 이유

**Files:** RecommendationReason.kt, RecommendationReasonTest.kt

- [ ] 마지막 안전 기회, 경로 이탈 최소, 주변 최단, 오래된 영업 정보 문구 테스트를 먼저 작성한다.
- [ ] 경로 데이터가 없을 때 경로·우회 근거가 나타나지 않는 테스트를 작성한다.
- [ ] 계산 사실만 사용하는 이유 생성기를 구현한다.
- [ ] feat: 추천 이유 문구 추가로 커밋한다.

## Task 4: Repository 계약과 합성 adapter

**Files:** 각 data 모듈 Repository, internal demo 구현, DemoGraph.kt와 계약 테스트

- [ ] 권한 거부, 미지원, stale, timeout, 경유지 2개, 콜백 실패 테스트를 작성한다.
- [ ] 모든 demo fixture가 DataOrigin.Synthetic임을 테스트한다.
- [ ] internal 합성 구현과 demo 조립을 구현한다.
- [ ] 실제 build가 합성 adapter를 참조하지 않는지 검사한다.
- [ ] feat: 데이터 계약과 합성 adapter 추가로 커밋한다.

## Task 5: ViewModel 상태 흐름

**Files:** AssistViewModel.kt, AssistUiState.kt, AssistViewModelTest.kt

- [ ] 감시에서 조회, 무안내, 후보, 오류로 가는 전이 테스트를 작성한다.
- [ ] 목적지 변경 시 이전 후보 폐기 테스트를 작성한다.
- [ ] 확인 연속 탭과 늦은 콜백이 한 번만 반영되는 테스트를 작성한다.
- [ ] 불변 StateFlow ViewModel을 구현한다.
- [ ] feat: 주유 추천 상태 흐름 추가로 커밋한다.

## Task 6: 지도 없는 B안 Compose UI

**Files:** feature/assist UI와 navigation, AssistFlowTest.kt

- [ ] 지도 요소 부재, 후보 3곳, 한 줄 이유, 합성 배지, 확인 한 번, 실패 상태 UI 테스트를 먼저 작성한다.
- [ ] 감시 홈, 추천, 후보, 확인, 결과, 정차 설정 화면을 구현한다.
- [ ] 주행 중 설정 입력을 숨기고 큰 터치 영역을 적용한다.
- [ ] UI 테스트와 1024x768 화면 검사를 통과시킨다.
- [ ] feat: 지도 없는 주유 추천 UI 추가로 커밋한다.

## Task 7: 실제 adapter 골격과 Pleos 검증

**Files:** AaosEnergyAdapter.kt, PleosNaviAdapter.kt, docs/pleos-verification.md, tools/pleos_probe.py

- [ ] AAOS property 지원·권한·status mapping 테스트를 작성한다.
- [ ] NaviHelper 경유지 0, 1, 2개와 timeout, callback correlation 테스트를 작성한다.
- [ ] 승인되지 않은 SDK는 feature flag 뒤에 두고 미지원 상태를 구현한다.
- [ ] RANGE_REMAINING과 활성 경로 동시 측정 절차를 문서화한다.
- [ ] feat: Pleos 연동 경계와 검증 도구 추가로 커밋한다.

## Task 8: 검증과 인계

**Files:** README.md, docs/architecture.md, docs/verification.md, docs/handoff.md

- [ ] doctor, verify, ui 명령을 실행한다.
- [ ] 합성·실제·미확인 범위와 실패 결과를 문서화한다.
- [ ] API 키·개인 경로·캐시·SDK 바이너리가 Git에 없는지 검사한다.
- [ ] 팀 검토용 PR을 만들고 검증 결과와 미확인 Pleos 항목을 적는다.

## Implementation Rulings

- 활성 목적지가 없을 때는 주변 주유소 추천으로 전환한다. 목적지가 있으면 주변 모드로 섞지 않는다.
- 동률 후보는 경로상 거리 또는 직선거리, 이름 순으로 안정적으로 정렬한다.
- 영업 정보가 신선하지 않으면 Open 가점을 주지 않고 Unknown과 확인 필요 문구를 사용한다.
- 알림 접근 범위는 기본 20km 또는 15분이며 FreshnessPolicy와 AlertPolicy로 주입한다.
- routeId와 stationId 조합을 사용해 같은 활성 경로에서 같은 추천을 한 번만 노출한다.
- NavigationRouteRepository는 setDestination과 addWaypoint를 분리하고 requestId, timeout, callback correlation을 계약에 포함한다.
- VehicleMotionRepository를 추가해 정차 중 설정 변경을 검증한다.
- 실제 Pleos SDK 바이너리가 없으면 real 빌드는 Unsupported를 명시하고 합성 데이터로 자동 대체하지 않는다.
- 1024x768 검증은 Compose 테스트의 고정 viewport와 스크린샷 산출물로 수행한다.
- tools/pleos_probe.py는 공개 SDK 계약을 검증하는 최소 수동 도구로만 두고, 확인되지 않은 SDK 호출을 구현하지 않는다.
