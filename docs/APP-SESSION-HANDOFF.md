# 앱 개발 세션 인계

2026-11-13 최신 후속: `feature/prepleos-hardening-20261113`과 [팀원 PLEOS 연결 인계](TEAMMATE-PLEOS-HANDOFF-2026-11-13.md)를 기준으로 한다. 웹·Android는 calm + 네 가지 32포즈 시트와 공용 시간표를 사용한다. 검증된 `NavigationTarget`은 station candidate에서 `WaypointRequest`까지 보존되고 누락·불일치면 SDK 호출 전 `Unsupported`다. HUN과 음성은 같은 추천 key/station 검증을 재사용하며, 실제 PLEOS HUN·Gleo·TTS 어댑터는 팀원이 연결한다. 아래 72프레임 설명은 11월 10일 고정 태그의 역사적 인계다.

2026-11-11 후속 제품 준비: 최신 `integrate/podong-approved`의 [제품 준비 기록](product-readiness-2026-11-11.md)을 먼저 읽는다. 회사 앱의 별도 세션에는 고정 UI 태그와 최신 통합 브랜치를 구분해서 적용한다. `AssistRuntime`은 Application과 서비스가 공유하며, 별도 AssistViewModel을 중복 생성하지 않는다. 새 목적지 설정/기존 경유지 추가 계약과 실제 POI 조립 경계는 최신 브랜치 기준이다.

## 다음 기기에서 그대로 전달할 문장

```text
최신 작업 브랜치를 받아 CONTINUE-HERE.md, docs/APPROVED-BASELINE.md, docs/APP-SESSION-HANDOFF.md, docs/ANDROID-INTEGRATION.md를 먼저 읽어줘. 현재 기준은 WAYFILL 소프트 선셋 UI와 calm + 네 가지 32포즈 공용 모션이야. feature/assist와 app의 기존 안전 계약을 보존하며 실제 PLEOS 차량 신호, 안정적인 주유 이벤트 ID, 검증된 좌표/도로 지표, NaviHelper, 시스템 HUN, Gleo/TTS를 연결해줘. NavigationTarget·추천 key·station ID를 우회하지 말고 SDK 성공 응답 전에는 성공으로 표시하지 마. 시연 숫자를 운영 기본값으로 쓰지 말고, WebView로 대체하지 말며, 빌드·기기 실행·중복 이벤트·출발 중단·Unknown/Unsupported를 확인해 docs/SESSION-PROGRESS.md에 기록해줘.
```

## 연결 순서

1. 기존 회사 앱의 현재 변경을 보존하고 새 작업 브랜치에서 시작한다. 이 저장소는 UI 모듈과 연결용 Android 호스트 앱을 포함한다. 회사 앱의 실제 SDK 연결을 덮어쓰지 말고 최신 포트 계약과 승인 UI를 통합한다.
2. `feature/assist`의 승인 UI를 사용한다. `PodongClips.kt`와 아틀라스, `PodongAnimation.kt`의 승인 버전을 유지한다. 상세 Gradle/JDK 설정은 [ANDROID-INTEGRATION.md](ANDROID-INTEGRATION.md)를 따른다.
3. ViewModel이 `FuelUiState`와 reducer 갱신을 소유한다. 실제 차량 상태 전까지 `Motion.Unknown`을 유지한다.
4. 검증된 주유 완료 데이터로 `RefuelConfirmed`를 전달한다. `seenIds`, 모션 설정을 이벤트 구독 전에 복원한다. 이벤트 재전달 시 같은 ID를 사용한다.
5. `onRequestWaypoint`에서 기존 내비에 요청하고, SDK 성공 응답에서만 `RouteAdded`, 실패 응답에서 `RouteFailed`를 전달한다.
6. 기존 추천 엔진의 결과를 `FuelRouteUi`로 전달한다. 연료·주유량·거리가 없으면 null 처리하고 실제로 확인한 full/low 신호만 사용한다.
7. 주유 전 분석을 주유 후에 재사용하지 않도록 최신 차량/경로 데이터와 확인 상태 정리 흐름을 연결한다.

## 완료 확인

- 회사 앱 빌드와 실제 화면 실행. 모듈 단위 빌드와 호스트 통합 결과를 구분한다.
- 추천 → 경유지 요청 → 실제 내비 응답 → 완료. 실패 시 다시 선택 가능.
- 정차 시 주유 완료 1회, 동일 이벤트 재전달/화면 재생성/프로세스 재시작 후 중복 인사 없음.
- 주행·상태 미확인에서는 큰 동작 취소, 재정차 시 지난 인사 재생 없음.
- 모션 OFF/접근성 모션 감소에서도 캐릭터와 메시지 유지.
- 주유량/거리 미수신 시 임의 수치 없음. 현재 주행 위치에 맞는 다음 주유소 표시.
- 실제 기기 성능·메모리·화면 크기와 지도 표면 검증.

새 리소스는 기존 정지 포즈 회전 구현과 섞지 않는다. 스프라이트의 표시 영역은 클립 전체에서 동일하며 프레임마다 크기를 다시 맞추지 않는다. 사용자 후속 요구에 따른 변경과 실제 기기 결과는 `docs/SESSION-PROGRESS.md`에 기록한다.
