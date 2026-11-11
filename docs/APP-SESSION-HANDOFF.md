# 앱 개발 세션 인계

## 다음 기기에서 그대로 전달할 문장

```text
https://github.com/kako-labs/fuel-pilot 의 integrate/podong-approved 최신 브랜치를 받아 기존 Pleos 앱에 UI를 연결해줘. 먼저 CONTINUE-HERE.md, docs/APPROVED-BASELINE.md, docs/APP-SESSION-HANDOFF.md, docs/ANDROID-INTEGRATION.md를 읽어줘. 승인된 UI는 web/index.html, 모션은 16프레임 정차 동작 4종 + calm 8프레임이야. 현재 통합 위치인 feature/assist의 dev.fuelpilot.ui 소스·리소스·테스트를 기준으로 기존 앱의 차량·추천·내비 로직을 보존하며 Compose UI와 콜백을 연결해줘. 실제 정차/주행 신호, 안정적인 주유 이벤트 ID, seenIds 저장/복원, 내비 성공·실패 응답을 연결해줘. 시연 숫자를 운영 기본값으로 쓰지 말고 기존 지도 표면에서 허용되는 방식인지 확인해줘. 웹 데모를 단순 WebView로 감싸는 방식으로 대체하지 말고, 빌드·실제 실행·중복 이벤트·출발 중단·데이터 미수신을 확인해줘. UI 세션의 승인 표현을 보존하고 진행/남은 통합 작업은 docs/SESSION-PROGRESS.md에 기록해줘.
```

## 연결 순서

1. 기존 회사 앱의 현재 변경을 보존하고 새 작업 브랜치에서 시작한다. 이 저장소는 UI 전달 모듈이며 회사 앱 전체를 대체하지 않는다.
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
