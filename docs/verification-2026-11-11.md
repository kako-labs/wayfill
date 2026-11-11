# 앱 통합 검증 · 2026-11-11

시작 커밋: `59fdbd5` (`integrate/podong-approved`). 승인된 72프레임 아틀라스·타이밍·표시 영역은 유지했다.

## 수정과 연결

- 초기 Unknown 스냅샷이 복원된 추천 노출 기록을 지우던 오류: 기존 실패 테스트로 재현 후 수정.
- 경로 변경/출발/주유로 무효가 된 버퍼 내비 요청이 늦은 수신자에게 전달되던 오류: 기존 실패 테스트로 재현 후 현재 pending ID/주유소 검증 추가.
- 저장 복원/쓰기 실패는 명시적 저장 오류 상태로 전환하고 새 안내 동작을 멈춘다. 저장되지 않은 주유 인사를 먼저 발행하지 않는다. 앱 재실행으로 복원을 다시 시도한다.
- 기본 Application/DataStore/의존성 컨테이너, 앱 수명의 ViewModel, 순차 판단 입력과 승인 UI를 연결했다.
- 실제 데이터 조회는 포트 계약을 통해 수행한다. 5초 읽기 제한, 10초 내비 응답 제한, SDK 예외·권한 거부·데이터 미지원·오래된 자료를 처리한다.
- 경로가 확인되지 않은 경우 주변 추천으로 전환하지 않는다. `RouteResult.Available(null)`로 목적지 없음이 확인된 경우에만 주변 모드다.
- 내비 요청은 현재 ID와 주유소가 일치할 때만 수행하며 출발·경로 변경 시 진행 중 호출을 취소한다. 콜백 일치 검증은 AssistViewModel에서 다시 수행한다.
- 단위 테스트의 합성 입력은 `src/test`에만 존재한다. 운영 기본 포트는 Unsupported이며 샘플 거리·좌표·주유소·성공 결과를 공급하지 않는다.

## 검증 기록

Task 6 최초 RED: ViewModel 20개 중 2개 실패. 수정 후 assist 42개 + lint 통과. 추가 I/O RED: 저장 읽기/쓰기 오류 2개와 데이터 읽기 제한 1개를 재현했다. 후속 전체 결과는 아래 최종 검증에 기록한다.

기존 Compose `ModifierParameter` lint 위반은 매개변수 순서만 정리했다. 시각/모션 리소스는 변경하지 않았다.

## Android 에뮬레이터 실행

Android 35 Pixel_8 에뮬레이터에서 debug APK 설치와 Activity 실행 성공. 1024×768 / density 160 화면에서 작은 뽀동이와 `이 환경에서는 주유 안내 정보를 지원하지 않아요.`를 확인했다. 추천 숫자·주유소·경유지 성공을 임의로 표시하지 않았으며 AndroidRuntime 오류가 없었다.

에뮬레이터 확인은 실제 Pleos 차량·권한·SDK 또는 내비 지도 위 표시 검증을 의미하지 않는다.

## 실제 SDK 연결 계약

다음 연결 지점은 `AppDependencies`: energy, location, stations, navigation, vehicleEvents다. 정차 상태와 안정적인 주유 이벤트 ID를 공급하는 SDK 구현을 주입해야 한다. route.id는 목적지/활성 경로 생명주기 변경을 구분해야 하며, 거리 갱신마다 새 ID를 발급하면 같은 추천이 반복된다. 각 어댑터는 Fresh/Stale/Unavailable/Unsupported를 정직하게 반환한다. coroutine 취소 시 실제 SDK 작업/콜백 해제도 어댑터가 처리해야 한다.

## 최종 검증

`python scripts/dev.py doctor`: JDK 17/Android SDK 준비 확인. `python scripts/dev.py verify`: `verifyAll` 성공, 1분 7초 / 291개 Gradle 작업. APK 생성 및 모든 모듈 lint 포함. Kotlin 테스트 집계는 77개(앱 8 / 도메인 14 / 데이터 포트 9 / assist 46), 웹 17개, Python 모듈 정책 2개이며 모두 실패 0건이다. `npm.cmd run verify`도 웹/네이티브 아틀라스·72프레임·외부 웹 의존성 없음 검사를 통과했다.

독립 리뷰의 두 Important 지적을 회귀 테스트 3개로 RED→GREEN 확인했다.

1. 저장이 지연돼도 출발/Unknown 및 진행 중 내비 취소는 즉시 처리한다. 단일 intent actor와 순서가 보장된 저장 writer를 분리했고, 저장 성공한 추천/주유 ID만 적극 노출한다. 과거 저장 응답이 현재 주행 상태를 덮어쓰지 않는다.
2. 차량 이벤트 스트림 오류는 Unknown/Unavailable로 전환한다. 잘못된 Parked 스냅샷으로 동작을 계속하지 않으며 앱 재실행 전까지 안내를 중단한다. 실제 SDK 어댑터의 재연결 전략은 후속 작업이다.

리뷰 Minor 한 건은 후속 UI 작업으로 남겼다: 앱 호스트에서 정차 전용 Settings 진입 버튼이 아직 없다. 설정 화면/영속 선호/intent 계약은 보존되어 있다.

리뷰가 판단을 보류한 실제 Pleos SDK 취소/권한과 차량 실행은 SDK/차량 환경이 없으므로 미검증으로 유지한다. 에뮬레이터 결과로 이를 통과했다고 대체하지 않는다.

앱 아이콘 누락은 승인된 calm 정지 이미지 원본을 런처에 그대로 연결해 해결했다. 웹/네이티브 모션 리소스와 타이밍에는 변경이 없다.

[에뮬레이터 1024×768 화면](../reference/android-unsupported-1024x768.png).
