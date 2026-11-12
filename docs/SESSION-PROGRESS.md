# 후속 세션 진행 기록

## 2026-11-11 SDK 외 제품 준비 후속

사용자 요청 ‘그거 다 해줘’에 따라 Motion Care와 비교해 남았던 홈·설정, 공유 런타임과 모니터링 서비스, 내비 목적지/경유지 계약, debug 네이티브 시연, 주유소 서버 경계, 화면 테스트/CI/제출 문서를 구현했다. 기준은 `f7b576f` 이후 같은 `integrate/podong-approved` 브랜치다. 승인된 캐릭터 아틀라스와 타이밍은 변경하지 않았다.

독립 리뷰에서 경로 진행 거리와 실제 주유소 접근 거리의 차이를 연료 여유 판단에 반영하고, 화면을 켠 상태에서 백그라운드 감시만 중지할 때 정차 상태가 Unknown으로 고착되는 문제를 수정했다. 실제 데이터에 검증된 연료 종류·좌표 변환·도로 거리가 없으면 추천하지 않는다. 최신 검증과 외부 연결 항목은 [제품 준비 기록](product-readiness-2026-11-11.md)에 모은다. 아래 과거 인계 상태와 혼동하지 않는다.

## 현재 인계 상태 · 2026-11-10

- UI: 사용자가 표현을 키운 16프레임 버전을 승인. 기준 코드 `95d0877`.
- 전달 모듈: Compose debug 빌드, Node 17개/Android 11개 테스트 통과.
- 배포: 고정 태그 `podong-ui-approved-2026-11-10`의 Release로 다른 기기에 전달.
- 회사 앱 통합: 미실행. 해당 앱 코드가 있는 기기에서 코드 세션이 진행.
- 실제 Pleos 실행: 미검증.

## 다음 세션 기록 형식

```text
날짜 / 기기 / UI 또는 앱 개발 세션:
작업 저장소·브랜치·시작 커밋:
수정한 동작과 파일:
실행한 검증과 결과:
UI와 코드 양쪽에 반영했는지:
남은 연결·기기 확인:
결과 커밋·PR:
```

두 세션은 같은 파일의 같은 부분을 동시에 수정하지 말고, 각 변경을 커밋한 뒤 상대 세션에서 해당 커밋을 확인해 반영한다. 충돌이 생기면 사용자 승인 기준과 이후 사용자 요청을 먼저 확인한다.

## 2026-11-11 집 PC 후속 개발

시작: integrate/podong-approved / 59fdbd5. Task 6 두 실패를 재현 후 수정(65e67d4), 추가 저장 I/O 실패와 조회 시간 제한도 RED→GREEN으로 확인했다. Task 7 앱 연결 및 7개 앱 통합 테스트를 추가했다. 에뮬레이터 1024×768에서 APK 실행/미지원 안내/캐릭터 표시를 확인했다. 승인된 72프레임 리소스는 유지했다.

다음은 실제 SDK 포트와 차량 기기 검증이다. 전체 최종 검증·리뷰 결과는 docs/verification-2026-11-11.md에 기록한다.

## 최종 리뷰 수정

독립 리뷰 Important 2건(저장 지연 중 출발 취소, 차량 이벤트 예외)을 재현한 3개 테스트를 고쳤다. Kotlin 테스트 77개가 통과했다. 앱 런처 아이콘은 기존 승인 뽀동이 이미지로 연결했다. 미완료 Minor: 정차 전용 Settings 진입 버튼. 실제 SDK/차량 연동은 다음 단계다. 상세 내용과 전체 검사 결과는 verification-2026-11-11.md 참조.

최종 전체 `python scripts/dev.py verify`는 빌드/lint 포함 통과(마지막 반복 검사 56초, 291개 작업). 웹17/정책2 검사도 통과했다. 구현 커밋: `65e67d4`(체크포인트 오류), `aa8c196`(앱 연결), `5f3207c`(리뷰 수정). GitHub 통합 브랜치가 구현 커밋과 일치함을 확인했다. 후속 문서 커밋도 같은 브랜치에 보존한다.

## 2026-11-12 UI/UX 제품 디테일 보강

- 작업 브랜치: `ui/ui-overview-20261112`, 시작 커밋 `32c7b94`.
- 웹: 현재 11개 상황을 실제 동작으로 살펴보는 `web/ui-gallery.html`을 추가했다. 추천 화면에 경로 분석 상태, 주행 가능/목적지/부족 거리, 추천 기준 카드 구조를 더했고 설정 화면에는 20~100km 여유거리 조작과 적용 조건을 명시했다.
- Android: `AppShell.kt`와 `FuelPilotUi.kt`의 홈·추천·통과 경고·장거리·설정을 같은 상태/거리/행동 위계로 정리했다. 기존 내비게이션 위임, 정차 제한, 영구 캐릭터 표시, 72프레임 아틀라스는 유지했다.
- 검증: `npm.cmd test` 18개 통과, `npm.cmd run verify` 통과, `python scripts/dev.py doctor` 통과. `python scripts/dev.py verify`와 직접 Gradle 컴파일은 소스 오류가 보고되기 전 Windows Gradle 8.12 transform 캐시의 임시 디렉터리 이동 실패로 중단됐다. 전역 캐시와 격리 임시 캐시 양쪽에서 동일하게 재현되어 환경 문제로 기록한다.
- 외부 참고 링크: Claude 아티팩트는 이 환경에서 Cloudflare 보안 확인 화면만 렌더링되어 직접 비교하지 못했다. 참고 화면 캡처를 받으면 2차 시각 보정을 진행한다.

### Motion Care 원본 확인 후 V2 방향 보강

사용자가 제공한 ZIP의 HTML과 실제 차량 캡처를 확인했다. 큰 히어로 장면, 상태/환경/설정 카드, 고정 하단 내비게이션, 일관된 깊이와 아이콘 체계를 Fuel Pilot의 주유 판단 흐름에 맞게 번역해 `web/product-vision.html`을 추가했다. 이 화면은 자체 지도를 만들지 않고 정차 홈에서 경로 판단과 추천 근거를 보여주는 제품 시안이다.

팀원 최초 뽀동이 시안과 기존 운영 이미지를 참고해 built-in imagegen으로 `web/assets/podong-v2-master-concept.png`를 만들었다. 더 낮고 말랑한 몸, 안정된 양발, 작은 손발, 부드러운 젤리·클레이 재질을 적용했다. 아직 운영 72프레임 아틀라스는 교체하지 않았으며, 외형 승인 뒤 5개 클립/72프레임을 웹과 Android에 함께 전환한다. 상세 기준은 `docs/PODONG-V2-DIRECTION.md`에 기록했다.

검증: Node 테스트 18개 통과, 패키지 검증 통과, `web/product-vision.js` 구문 검사 통과. 최종 렌더는 `docs/screenshots/fuel-pilot-product-v2-20261112.png`에 저장했다.

## 2026-11-12 Dual-mode companion and background HUN documentation

- Added `docs/DUAL-MODE-IMPLEMENTATION.md` describing the shared companion/HUN runtime, exact motion and action safety rules, reused reducer/persistence/navigation boundaries, validation matrix, and remaining Pleos SDK/device work.
- Documented the implemented split: monitoring starts only from a visible parked UI, while a running session may deliver and accept an exact key/station-correlated recommendation in either Parked or Moving; Unknown, stale/unsupported/unavailable data, refuel/celebration, duplicate pending work, and mismatches remain blocked.
- Actual validation observed: process-local `python scripts/dev.py doctor` passed and `verifyArchitecture` passed. Feature tests and full `python scripts/dev.py verify` did not reach Kotlin tests because Gradle 8.12 failed moving transform-cache temporary workspaces to immutable locations, including with an isolated transform cache. No Android test pass count is claimed.
- Production Pleos energy, location, vehicle events, and navigation adapters remain Unsupported; Pleos-native HUN approval and actual device/vehicle behavior remain unverified. No commit was created.
