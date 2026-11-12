# 후속 세션 진행 기록

## 2026-11-13 확정 디자인의 PLEOS 전 제품 작업

사용자가 잠든 동안 설계·구현·일반 Android 검증을 맡겼다. 새 64개 셀의 동작을 네이티브/웹에 연결하고 홈·안내 추천을 공유하며 주유/내비 상태와 2열 설정을 정리했다. 확인된 주유 기록의 90일/100건 저장·상세·삭제·실패 처리를 추가했다. 삭제와 중복 방지 정보를 분리하고 백업/기기 전송 제외 규칙을 넣었다. 주유 직후 재평가, 같은 추천의 인사 재시작 억제, debug 시연 런처와 release 격리도 다뤘다.

현재 브랜치는 `product/soft-sunset-prepleos-20261113`이며 원격 push는 하지 않는다. 실제 구현·검증·남은 실제 SDK/공급자 연결은 [최신 인계](PRE-PLEOS-READINESS-2026-11-13.md)를, 사용자가 볼 화면은 [갤러리](screen-gallery.html)를 기준으로 한다. 아래 날짜 기록의 테스트 숫자와 정지 자산 한계는 각 시점의 역사다.

## 2026-11-12 소프트 선셋 선택 후 구현

사용자가 외부 생성한 석양 주유소 홈과 도로 띠 뽀동이를 선택했다. 승인 원본을 `design/approved-sunset/`에 보존하고, 내장 이미지 도구로 투명 캐릭터와 글자 없는 배경을 각각 만들어 웹/Android에 같은 PNG로 넣었다. 정차 홈을 위쪽 메시지·거리·캐릭터와 아래쪽 추천·행동 패널로 바꾸고, 실제 Android 메뉴를 하단으로 옮겼다. 좁은 창·낮은 창·대기·설정 등 제품 캐릭터도 새 외형으로 통일했다.

새 캐릭터는 정지 이미지다. 기존 프레임을 섞지 않으며 이벤트 수명/중복/취소 흐름만 유지한다. 새로운 표정·주유 인사는 별도 프레임 제작이 남았다. 실제 PLEOS·실차 확인도 미실행이다. 적용과 검증은 [소프트 선셋 구현 기록](SUNSET-IMPLEMENTATION-2026-11-12.md)에 남긴다. 로컬 변경이며 커밋·푸시는 하지 않았다.

## 2026-11-12 Motion Care 참고 디자인 재작업

사용자가 기존 갈색 카드와 임의 우측 상단 알림 배치를 거부해 Motion Care의 실제 홈/시스템 알림 캡처를 다시 확인했다. 네이비·골드 정차 홈, 내비 옆 작은 창, 차량 연결 대기 화면을 웹과 Android에 반영했다. 알림은 OS 위치를 흉내 낸 내비 오버레이를 없애고 내용 시안과 실제 참고 캡처를 분리했다. `web/companion-preview.html`은 기존 경로 그대로 새 홈으로 시작한다.

전체 verify 성공(단위 테스트 결과 155개, 실패/오류/건너뜀 0), 마지막 밀도 조정의 관련 빌드·단위 테스트·lint 성공. 일반 Android 기기 테스트 15개 통과, 기존 나머지 7개 미실행. 웹/네이티브 캡처를 직접 열어 검토했다. 실제 PLEOS·실차 HUN 검증과 사용자의 디자인 확인은 별개다. 상세 결과는 [디자인 재작업 기록](REDESIGN-2026-11-12.md)에 남겼다. 로컬 변경만 있으며 커밋·푸시는 하지 않았다.

## 2026-11-12 내비 옆 작은 창·백그라운드 안내 후속

`94d0c7d`에서 `ui/adaptive-companion-20261112` 브랜치를 만들었다. Motion Care 통합 기록의 공통 근거·차량 터치 크기·긴 문구·실제 화면 검토를 Fuel Pilot에 적용했다. 작은 앱 창의 추천 이유/주유소/거리/행동을 별도 행으로 배치하고, 짧은 창은 가로 버튼, 좁은 창은 세로 버튼을 쓴다. 앱과 알림은 `FuelRecommendationCopy`를 공유하고, 앱 숨김도 독립적으로 관찰한다. 미룬 추천이 앱을 숨겼다는 이유로 재노출되지 않게 했다.

출발 시 남아 있던 정차 추천이 즉시 재노출되는 reducer 실패를 수정하고, 차량 상태 변화가 새 평가를 즉시 요청하도록 coordinator를 보완했다. 새 주행 평가를 기다리는 동안 이전 요청/추천을 재사용하지 않는다. 컴파일되지 않았던 헤더 수신자·알림 기록 처리, 테스트의 잘못된 import, SDK 권한 lint와 사용하지 않는 알림 문구도 수정했다. SDK 포트에서 권한을 다시 확인하고 비동기 권한 회수는 기존 Unsupported/예외 처리로 이어진다.

제품 기준·연결 지점은 [ADAPTIVE-COMPANION.md](ADAPTIVE-COMPANION.md), 검증 결과와 한계는 [verification-adaptive-2026-11-12.md](verification-adaptive-2026-11-12.md)에 기록한다. 웹 비교는 `web/companion-preview.html`이며 알림은 HTML 시안이다. 실제 PLEOS/실차 및 OS HUN 검증으로 취급하지 않는다. 운영 72프레임 아틀라스는 변경하지 않았다.

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

## 최종 인계 검증 · 2026-11-13

JVM 169 / 일반 Android instrumentation 27 / 웹 24 / 개발 도구 29 / 서버 9 통과. 건너뜀 0. debug·release unsigned·AndroidTest APK 빌드와 lint 성공. 최종 캡처를 얻기 위한 VisualReviewTest 1개 재실행도 통과했으며 27개 합계에 중복 가산하지 않는다. 최종 Android 홈/추천/요청·성공/인사/주행/기록·상세/설정 PNG를 직접 검토했다. 화면 모음 15개 이미지 로딩·확대·이동·모바일 폭을 브라우저에서 확인했다. 실제 PLEOS/실차/실공급자/원격 CI/서버 배포는 미실행. 자세한 실패 이력과 남은 연결은 PRE-PLEOS-READINESS-2026-11-13.md 참조.
