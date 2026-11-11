# 승인 UI 통합 설계 검토 보강

이 문서는 `2026-11-11-approved-ui-integration-design.md`의 일부이며, Orca 읽기 전용 구현자 검토에서 발견한 차단 위험을 보강한다.

## 데이터 포트와 Unsupported 범위

현재 `data:energy`, `data:location`, `data:poi`, `data:navigation`은 Android 모듈 골격이며 실제 회사 SDK 어댑터가 아니다. 통합에서는 각 모듈에 최소 포트를 정의하고 SDK·권한·기기 신호가 없을 때 명시적으로 실패한다.

- 에너지/위치/경로 읽기: `Fresh`, `Stale`, `Unavailable`, `Unsupported`
- 주유소 검색: 후보 목록 또는 `Unavailable/Unsupported`
- 내비 경유지 요청: request ID가 포함된 `Succeeded`, `Failed`, `TimedOut`, `Cancelled`, `Unsupported`

실제 SDK 구현은 SDK가 제공되는 후속 작업이다. 이번 제품 경로는 `Unsupported`를 UI의 확인 불가 상태로 전달하며 샘플 후보, 임의 위치, 임의 연료량을 반환하지 않는다.

## 판단 결과와 표시 입력

`FuelDecision`만으로는 UI가 요구하는 현재 주행 가능 거리, 목적지 거리, 마지막 안전 주유소 이후 후보를 모두 복원할 수 없다. UI 어댑터는 `FuelDecision`, 평가에 사용한 `FuelDecisionInput`, route snapshot ID, 신호 상태, route·mode·primary station으로 만든 안정적 recommendation key를 함께 받는다.

도메인 엔진은 `LAST_SAFE_STOP` 결과에 다음 경로상 후보와 그 후보 도착 예상 잔여 거리를 반환하도록 확장한다. UI 어댑터가 원본 후보를 다시 순위화하거나 안전 판단을 재구현하지 않는다. `AlertPolicy`에는 일반 추천과 “지나치기 전” 경고를 구분할 거리/시간 임계값을 둔다.

## 화면 상태 우선순위

ViewModel은 다음 순서를 단일 직렬 이벤트 경로에서 적용한다.

1. `Motion.Moving/Unknown`: 큰 동작과 상세 화면을 즉시 중단하고 차분한 위젯 또는 확인 불가 상태로 축소한다.
2. 정차 중 새로 확인된 주유 완료: `Refuel`. 같은 ID는 다시 표시하지 않는다.
3. 정차 중 활성 내비 요청: `Adding`; 동일 request ID의 결과만 반영한다.
4. 내비 성공: `Added`; 실패/timeout/Unsupported: 재시도 가능한 `Recommendation`과 명시적 오류.
5. `Recommendation(LAST_SAFE_STOP)`: 일반 임계 구간은 `Recommendation`, 지나치기 임계 구간은 `Passing`.
6. `Monitoring`: CTA 없는 차분한 위젯. `NoAction`: assist 오버레이를 숨긴다.
7. `Unavailable/Stale/Unsupported`: 임의 추천 없이 확인 중/사용 불가 표현을 사용한다.

`Analysis`, `Settings`, `LongDrive`는 정차 중 사용자 진입 화면이며 새 안전 경고, 주유 완료, 출발 이벤트가 우선한다. 같은 recommendation key는 경로 생명주기에서 한 번만 적극 노출하고, route ID 또는 목적지가 바뀌면 노출 상태를 초기화한다.

## 내비 요청 효과 계약

Compose onClick은 외부 SDK를 직접 호출하지 않고 ViewModel에 하나의 `RequestWaypoint(stationId)` 의도만 보낸다. ViewModel은 pending 상태와 고유 request ID를 원자적으로 만든 뒤 `NavigationEffect.AddWaypoint(requestId, stationId)`를 한 번 방출한다.

- pending 요청 중 연속 탭은 무시한다.
- 콜백은 request ID와 station ID가 모두 현재 pending 요청과 같을 때만 반영한다.
- timeout, 취소, `Unsupported`를 구분한다.
- route 변경 또는 출발로 무효가 되면 cancel하고 늦은 콜백을 버린다.
- 같은 주유소 재요청에도 새 request ID를 사용한다.

## 영속 저장 계약

호스트 저장 계층에 처리한 주유 이벤트 ID/처리 시각, 뽀동이 동작 설정, 접근성 모션 감소 설정, 현재 route ID, 마지막 recommendation key를 보존하고 차량/주유 이벤트 구독 전에 복원한다. 처리 ID는 최대 256개, 90일 보존을 초기 정책으로 한다.

`celebration`과 단조 시계 기반 `startedAtMs`는 프로세스 재시작 후 복원하지 않는다. 같은 프로세스의 화면 재생성에서는 ViewModel이 유지한다. 저장 포트의 Android 구현은 Preferences DataStore를 기본으로 하되 도메인과 reducer는 저장 기술에 의존하지 않는다.

## 추가 검증

- `python scripts/dev.py doctor`, `python scripts/dev.py verify`
- STALE/UNAVAILABLE/Unsupported 표시 구분
- 빠른 이중 탭, timeout, 취소, 순서가 뒤바뀐 과거 콜백
- route/목적지 변경 시 추천 노출 상태 초기화
- 실제 저장소에 쓴 뒤 새 ViewModel에서 이벤트 중복 억제 복원
- Android 1024×768 렌더링 회귀, 390px 웹 기준, 모션 감소 설정
