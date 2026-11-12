# 현재 이어갈 위치 · 2026-11-13

확정 디자인의 최신 GitHub 인계 브랜치는 `product/soft-sunset-prepleos-20261113`이다. [현재 제품 인계](docs/PRE-PLEOS-READINESS-2026-11-13.md), [화면 모음](docs/screen-gallery.html), [다른 기기 이동](docs/CROSS-DEVICE-2026-11-13.md)을 먼저 읽는다. [11월 13일 최신 Release](https://github.com/kako-labs/fuel-pilot/releases/tag/fuel-pilot-soft-sunset-2026-11-13) 또는 다음 Git 명령으로 받는다. 아래 과거 브랜치·Release는 이전 기록이다.

```powershell
git clone --branch product/soft-sunset-prepleos-20261113 --single-branch https://github.com/kako-labs/fuel-pilot.git fuel-pilot-sunset
cd fuel-pilot-sunset
git log -2 --oneline
```

이미 다른 브랜치에서 작업 중이면 변경을 보존한 뒤 fetch하고 별도 worktree를 만드는 방법이 안전하다. 기본 main에는 이번 코드가 없다.

---

# FUEL PILOT 이어가기 · 2026-11-11

**찾지 못했던 문제를 막기 위한 고정 진입점:** [전체 인계 Release](https://github.com/kako-labs/fuel-pilot/releases/tag/fuel-pilot-handoff-2026-11-11)와 [다른 노트북 인계 문서](docs/CROSS-DEVICE-2026-11-11.md)를 먼저 확인한다. 기본 `main`은 초기 상태이며 현재 앱은 `integrate/podong-approved`에 있다.

## 최신 코드 받기

```powershell
git clone https://github.com/kako-labs/fuel-pilot.git
cd fuel-pilot
git switch --track origin/integrate/podong-approved
git log -3 --oneline
python scripts/dev.py doctor
```

같은 폴더를 이미 사용 중이면 기존 변경을 보존하고 fetch/pull한 뒤 이 브랜치를 사용한다. `main`이나 11월 10일 UI 태그부터 개발을 다시 시작하지 않는다. 태그는 승인 UI 원본으로 유지된다.

## 오늘 이어서 처리한 내용

후속 제품 준비 작업은 [제품 준비 기록](docs/product-readiness-2026-11-11.md)을 먼저 확인한다. 홈·정차 전용 설정, Application 공유 런타임, 사용자 시작 모니터링 서비스, 목적지/경유지 요청 구분, debug 전용 네이티브 시연, 주유소 서버 경계와 CI/제출 문서를 추가했다. 실제 SDK·서버 키·좌표 변환·도로 거리·차량 기기 검증은 이 문서의 외부 연결 항목으로 구분한다. 아래 초기 통합 기록만 보고 SDK 외 작업이 모두 검증됐다고 판단하지 않는다.

Task 6의 복원 초기 Unknown/무효 버퍼 요청 오류를 고쳤다. 저장 읽기/쓰기 실패 시 미기록 주유 인사를 발행하지 않도록 처리했다. Task 7의 Application·DataStore·포트·판단 엔진·AssistViewModel·승인 UI·내비 응답 연결을 추가했다. 데이터 조회 제한과 내비 취소/시간 초과를 검증했다. 에뮬레이터에서 APK를 실행해 실제 미지원 상태와 뽀동이 표시를 확인했다.

현재 검증의 세부 결과는 [docs/verification-2026-11-11.md](docs/verification-2026-11-11.md), 작업 기록은 [docs/SESSION-PROGRESS.md](docs/SESSION-PROGRESS.md)를 확인한다.

## 다음 코드 세션에 전달

> kako-labs/fuel-pilot의 integrate/podong-approved 최신 코드를 받고 AGENTS.md, CONTINUE-HERE.md, docs/handoff.md, docs/ANDROID-INTEGRATION.md를 먼저 읽어줘. 승인 UI는 유지해. AppDependencies의 실제 Pleos 포트 연결이 다음 단계야. 실제 차량 주행 가능 거리와 정차 상태, 활성 내비 경로, 취소 가능한 경유지 추가 응답을 확인하고, 다음으로 실제 주유소 데이터와 검증된 주유 이벤트를 연결해줘. SDK/권한이 없으면 Unsupported를 유지하고, 시연 값을 운영 데이터로 대체하지 마. 실차 검증과 에뮬레이터 검증을 구분해 기록해줘.

운영 연결은 `FuelPilotApplication.dependencies`에서 한다. `NavigationCapabilities`와 `WaypointRequest.mode`를 확인하고, 주유소는 `createProductionStations`에 실제 차량 연료·검증된 좌표 변환·도로 거리 포트를 전달한다. `providerDistanceMeters`를 경로 거리로 복사하지 않는다. debug 앱 홈의 ‘시연 모드 · 예시 데이터’는 운영 데이터와 별도이며 release에서는 사용할 수 없다. 상세 계약은 [데이터 공급자 문서](docs/data-provider.md)에 있다.

## 다음 UI 세션

72프레임 웹 원본은 그대로 남아 있다. 실제 앱 렌더링은 feature/assist다. 다른 UI 변경 요청이 없으면 승인된 캐릭터·색·문구·정차 표현·작은 주행 아바타를 유지한다. UI 수정 시 웹과 네이티브에 필요한 차이를 SESSION-PROGRESS.md에 기록한다.

## 검증 명령

```powershell
npm.cmd test
npm.cmd run verify
python -m unittest discover -s scripts/tests -v
python scripts/dev.py doctor
python scripts/dev.py verify
python -m unittest discover -s server/tests -v
python scripts/ui_tests.py --connected
```

실제 Pleos SDK와 지도 표면 권한, 차량 기기에서의 성능/동작 검증은 아직 남아 있다. 이 앱은 SDK가 없는 일반 Android 에뮬레이터에서 주유 안내 미지원 상태를 표시하는 것이 정상이다.

## 독립 리뷰 후 추가 수정

느린 저장이 출발 취소를 지연시키지 않도록 intent actor와 저장 writer를 분리했다. 저장 전 새 주유 인사를 발행하지 않는 원칙은 유지한다. 차량 이벤트 구독이 예외로 끝나면 Unknown/Unavailable로 비활성화한다. 초기 리뷰에서 남겼던 Settings 진입은 후속 제품 준비에서 해결했다.
