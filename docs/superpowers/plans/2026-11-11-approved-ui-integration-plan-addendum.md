# Approved UI Integration Plan Addendum

이 파일은 `2026-11-11-approved-ui-integration.md`와 함께 실행한다.

## 고정 타입

- `enum class AssistAvailability { READY, STALE, UNAVAILABLE, UNSUPPORTED }`
- `fun FuelDecisionUiMapper.map(input: AssistPresentationInput): AssistPresentation`
- `AssistPresentationInput(decision: FuelDecision, evaluatedInput: FuelDecisionInput, routeSnapshotId: String?)`
- `AssistPresentation(route: FuelRouteUi, targetScreen: Screen?, recommendationKey: String?, availability: AssistAvailability)`
- `AssistEffect.AddWaypoint(requestId: String, stationId: String)`

## 자체 검토 결과

- 기본 설계의 병합, 승인 UI 보존, 모듈 이전, 앱 연결, 원격 푸시 요구는 Tasks 1, 4, 7, 8에 연결돼 있다.
- 검토 보강의 Unsupported 포트, passing 후보, 화면 우선순위, request ID, 영속 저장 요구는 Tasks 2, 3, 5, 6에 연결돼 있다.
- Wave 1의 Tasks 2, 3, 4는 각각 `core/model`, `data`, `feature/assist`를 소유해 파일이 겹치지 않는다.
- Task 5 이후의 타입 이름은 위 고정 타입 및 Tasks 2–4의 Interfaces와 일치한다.
- 다섯 Review Focus 항목은 각각 소유 Task의 테스트 단계에 포함돼 있다.
