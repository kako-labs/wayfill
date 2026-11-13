# WAYFILL 앱 개발 인계 · 2026-11-11

현재 작업 브랜치는 `integrate/podong-approved`다. 시작은 루트 [CONTINUE-HERE.md](../CONTINUE-HERE.md). 11월 10일 태그는 승인 UI 원본 보존용이며, 최신 앱 통합 작업의 시작점으로 되돌리지 않는다.

## 현재 구현

- 결정 엔진과 마지막 안전 주유소/다음 후보, UI mapper를 연결했다.
- 승인된 Compose UI와 72프레임 모션을 `feature/assist`에서 사용한다.
- AssistViewModel이 화면 우선순위, 현재 내비 요청 ID, 주유 중복 ID와 모션 설정을 관리한다.
- Preferences DataStore 복원 후에만 실시간 입력 구독을 시작한다. 처리 ID는 최대 256개/90일 보존, 애니메이션 시작 시간은 프로세스 재시작 후 복원하지 않는다.
- 초기 Unknown 스냅샷은 복원된 추천 기록을 지우지 않는다. 취소된 버퍼 요청은 뒤늦게 전달하지 않는다. 저장 실패는 명시적 오류로 전환하고 새 동작을 멈춘다.
- AppDependencies → AppCoordinator → 판단 엔진/mapper → AssistViewModel → FuelPilotUi 연결을 추가했다.
- 앱 수명의 ViewModel이 내비 효과를 수집한다. 화면 회전이 요청을 재시작하지 않으며 출발/경로 변경은 진행 중 요청을 취소한다. 요청 ID와 주유소가 일치하는 응답만 반영한다.
- 읽기 5초/내비 10초 제한, 실제 SDK 미지원/권한 거부/오래된 자료를 명시적으로 처리한다. 확인된 목적지 없음일 때만 주변 모드를 사용한다.
- Android 35 에뮬레이터에서 APK 실행과 1024×768 미지원 안내/뽀동이 표시를 확인했다.

## 다음 작업: 실제 SDK 포트

1. `AppDependencies.energy`: 검증된 주행 가능 거리, 데이터 신선도와 권한 상태.
2. `navigation`: 활성 경로의 안정적인 ID·거리, 경유지 추가와 취소 가능한 실제 응답.
3. `vehicleEvents`: 실제 정차/주행/미확인 상태와 안정적인 주유 완료 이벤트 ID. 임의 연료 상승만으로 확정하지 않는다.
4. `location`, `stations`: 실제 좌표·주유소 데이터, 경로 진입 거리·영업 정보·유종 호환성.
5. 실차에서 추천 → 경유지 요청 → 성공/실패 응답 → 출발 중단을 확인한다. SDK/권한이 없으면 Unsupported를 유지한다.

별도 앱 내 지도는 추가하지 않는다. 실제 내비 표면에 표시할 수 있는 권한과 SDK 방식은 별도 확인 대상이다. APK가 설치/실행된 것과 실제 차량 연동을 구분한다.

## 검증

`python scripts/dev.py doctor`, `python scripts/dev.py verify`, `npm.cmd test`, `npm.cmd run verify`, `python -m unittest discover -s scripts/tests -v`.

자세한 RED/GREEN·빌드·기기 결과는 [verification-2026-11-11.md](verification-2026-11-11.md), 후속 작업은 [SESSION-PROGRESS.md](SESSION-PROGRESS.md)에 기록한다. 이 브랜치에서 사용자가 승인한 UI를 다시 디자인하는 작업은 요청되지 않았다.
