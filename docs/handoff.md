# FUEL PILOT 개발 인계

최종 갱신: 2026-11-10

## 한 문장 정의

FUEL PILOT은 단순히 연료 부족 시 주변 주유소를 보여 주는 앱이 아니다. 남은 주행 가능 거리와 활성 내비게이션 경로를 함께 분석해, 운전자가 현재 경로에서 주유하기 좋은 마지막 안전 기회를 놓치기 전에 한 번만 알려 주고 승인 시 내장 내비 경유지로 전달하는 차량용 앱이다.

> 연료가 부족해진 다음이 아니라, 주유하기 좋은 마지막 기회를 놓치기 전에.

## 사용자가 확정한 핵심 동작

### 활성 목적지가 있을 때

1. 차량에서 남은 주행 가능 거리를 읽는다.
2. 내비에서 목적지와 남은 경로 거리를 읽는다.
3. 목적지 도착 시 남을 예상 주행 가능 거리를 계산한다.
4. 사용자가 정한 최소 여유거리보다 적게 남을 때 경로상 주유소를 평가한다.
5. 도착 후에도 최소 여유거리를 남길 수 있는 후보 중 마지막 안전 주유 기회를 찾는다.
6. 후보까지 20km 이내이거나 도착 예상 15분 이내일 때 추천한다.
7. 같은 활성 경로에서 같은 주유소는 한 번만 추천한다.
8. 운전자가 확인하면 내장 내비에 경유지로 추가하고, 성공 콜백을 받은 뒤에만 완료로 표시한다.

### 활성 목적지가 없을 때

이때만 일반적인 주변 주유소 추천으로 전환한다. 현재 위치에서 도달 가능하고, 차량 유종과 호환되며, 영업 중인 후보를 우선해 가까운 순서로 보여 준다. 목적지가 있는데 주변 추천을 섞지 않는다.

### 한 줄 추천 이유

모든 추천에는 선택 이유를 쉬운 한 줄로 표시한다. 계산으로 확인한 사실만 말하며 경로 자료가 없으면 경로 또는 우회 표현을 만들지 않는다.

- 마지막 안전 기회: `이곳을 지나면 다음 주유소까지 연료 여유가 부족할 수 있어요.`
- 목적지 없는 주변 추천: `현재 위치에서 약 2km로, 도달 가능한 주유소 중 가장 가까워요.`
- 확인된 최소 이탈: `현재 경로에서 3분만 벗어나 들를 수 있어요.`
- 오래된 영업 정보: `현재 위치에서 약 2km로 가깝지만, 영업 정보는 출발 전에 확인해 주세요.`

## 현재 구현 상태

작업 브랜치: `song0328/last-safe-fuel-mvp`

주요 커밋:

- `4a30ca4` 설계와 구현 계획
- `8853079` Android 35, JDK 17 프로젝트 골격과 모듈 경계
- `2d24b70` 마지막 안전 주유 판단 엔진과 테스트
- `91edc4f` 한 줄 추천 이유 생성기와 테스트

완료된 내용:

- 새 독립 저장소와 기능 브랜치
- `app`, `core:model`, 에너지·위치·POI·내비 data 모듈, `feature:assist` 골격
- 허용되지 않은 모듈 의존성을 막는 경계 검사
- 목적지 도착 여유가 충분하면 `NoAction`
- 다음 후보가 안전하지 않을 때 현재 후보를 `LAST_SAFE_STOP`으로 추천
- 목적지가 없으면 주변 추천 모드로 전환
- 영업 종료·유종 불일치·도달 불가 후보 제외
- 오래된 에너지 데이터와 후보 없음 상태 처리
- 거리 자료만 있어도 20km 안에서 마지막 안전 기회 추천
- 실제 근거만 쓰는 한국어 한 줄 이유

핵심 테스트는 `:core:model:test`에서 통과했다. 모듈 경계 Python 테스트 2개도 통과했다.

## 아직 구현하지 않은 내용

다음 작업은 설계 계획의 Task 4부터 이어간다.

1. Repository 계약과 합성 adapter
   - 에너지, 위치, 차량 정차 상태, POI, 내비 경로 계약
   - 권한 거부, 미지원, stale, timeout, 콜백 실패
   - 모든 합성 fixture에 `DataOrigin.SYNTHETIC` 표시
2. 상태 오케스트레이션
   - Monitoring, NoAction, CandidatesReady, Confirming, Submitting, Delivered, Unavailable
   - 목적지 변경 시 이전 추천 취소
   - requestId 기반 콜백 연결과 중복 제출 방지
3. UI
   - 사용자 승인 전 세부 패널 모양을 고정하지 않는다.
   - 결정 상태와 Compose UI 모양을 분리한다.
4. 실제 adapter 경계
   - AAOS `RANGE_REMAINING`
   - Pleos NaviHelper의 활성 경로, 경유지 추가, 경로 재탐색
   - 실제 주유소 POI 공급자
5. 다른 PC에서 `verifyAll`과 1024x768 차량 화면 검증

## UI 방향 변경 사항

초기 설계의 밝은 B안은 폐기되었다. 확정된 기준은 다크 허니 골드이며, 독립 앱 화면보다 내비 지도 위 팝업과 위젯이 주행 흐름의 중심이다.

- 바탕: 웜 그래파이트 `#171512`, 가장 어두운 바탕 `#0C0B09`
- 추천·실행·연료 리본: 허니 골드 `#E3A33C`, 글자용 `#F0C46E`
- 마지막 기회와 여유선 아래 경고만 코랄 `#F2654E`, 글자용 `#FF9A88`
- 지도, 경로선, 현재 위치는 내비의 파란색 유지
- 한글 IBM Plex Sans KR, 숫자 Space Grotesk
- 캐릭터 `뽀동이` 유지. 사용자가 특히 선호한다.
- 주행 중에는 작은 얼굴만, 정차 중 완료 화면에서는 크게 사용할 수 있다.
- 그라데이션·빛 번짐·안쪽 그림자 없이 한 겹 패널을 우선한다.
- 카드 안의 카드 대신 구분선 목록, 알약 태그 대신 색 있는 글자, 핵심 숫자 하나와 계산식 한 줄을 사용한다.
- 버튼은 차량용 터치 크기로 크게 하고 주 버튼 하나만 골드로 둔다.

화면 후보:

1. 경로 설정 직후 주유 추천 팝업
2. 주행 중 연료 여유 위젯
3. 마지막 안전 주유 기회 팝업
4. 경유지 추가·재탐색 완료
5. 주유 완료와 뽀동이
6. 앱의 경로 연료 분석
7. 앱의 주유 안내 설정

주의: 패널 세부 디자인, 유가 표시, 음성 응답, 주유 완료 화면은 아직 사용자 검토 중이다. 범위를 임의로 넓히지 말고 UI 작업 전에 사용자에게 확인한다. 뽀동이 원본 PNG도 사용자에게 받아야 한다.

## 계산 정책

- 기본 최소 여유거리 50km
- 정차 중에만 20~100km, 10km 단위로 변경
- 거리 표시는 정수 km, 내부 계산은 `Double`
- 도달 가능한 주변 후보 조건은 후보 거리보다 현재 주행 가능 거리가 커야 함
- 영업 상태는 Open, Closed, Unknown
- 오래된 영업 정보는 Unknown으로 낮추고 확인 필요 문구 표시
- 기본 신선도: 에너지 10초, 위치 30초, 경로 30초, 영업 정보 30분
- 기본 알림 접근 범위: 20km 또는 15분
- 동일 경로 중복 억제 키: `routeId + stationId`
- 목적지 변경 시 중복 억제 상태 초기화
- 내비 전달은 `requestId`, timeout, callback correlation이 필요
- 실제 모드 실패 시 합성 데이터로 조용히 대체하지 않고 Unsupported 또는 Unavailable 표시

## 실제 연동에서 확인할 것

첫 번째 검증 질문:

> Pleos에서 남은 주행 가능 거리와 활성 내비게이션의 목적지·남은 거리를 동시에 안정적으로 읽을 수 있는가?

현재 확인 범위:

- AAOS에는 `RANGE_REMAINING`, `FUEL_LEVEL`, `FUEL_LEVEL_LOW`가 있으나 `CAR_ENERGY` 권한과 차종 지원 여부를 확인해야 한다.
- Pleos NaviHelper에는 목적지·경유지 정보, `remainDistance`, 경유지 추가와 경로 재탐색 관련 기능이 문서에 나타난다.
- 공개 Pleos 주유소 POI 검색과 정확한 우회 시간 제공 여부는 미확정이다.
- 외부 POI를 쓰더라도 API 키를 APK나 Git에 넣지 않는다.

## 이 노트북에서의 검증 제약

JDK 17과 Android SDK 35는 설치되어 있다. 하지만 회사 노트북에서 Gradle 8.12가 Android 의존성 변환 캐시의 임시 폴더를 최종 폴더로 이동하지 못하는 오류를 반복했다. 새 프로젝트 전용 캐시에서도 동일했다.

- 성공: `python -m unittest discover -s scripts/tests -v`
- 성공: `./gradlew verifyArchitecture :core:model:test --no-daemon --max-workers=1`
- 성공: `./gradlew :core:model:test --no-daemon --max-workers=1`
- 실패: `./gradlew verifyAll --no-daemon --max-workers=1`
- 실패 원인: `Could not move temporary workspace ... transforms ... to immutable location`

이는 현재까지 Android 소스 컴파일 오류가 아니라 이 노트북의 Gradle 캐시 이동 문제다. 다른 PC에서는 먼저 `verifyAll`을 실행해 실제 Android 빌드와 lint를 확인한다.

## 다른 PC에서 시작하는 명령

```powershell
git clone https://github.com/kako-labs/fuel-pilot.git
cd fuel-pilot
git switch song0328/last-safe-fuel-mvp
python scripts/dev.py doctor
./gradlew :core:model:test --no-daemon --max-workers=1
./gradlew verifyAll --no-daemon --max-workers=1
```

JDK는 17, Android SDK는 compileSdk 35와 build-tools 35.0.0이 필요하다. `JAVA_HOME`, `ANDROID_HOME` 또는 `ANDROID_SDK_ROOT`를 설정한다. `local.properties`, SDK 바이너리, 캐시, API 키는 커밋하지 않는다.

다른 AI 세션에 전달할 첫 문장:

> `kako-labs/fuel-pilot` 저장소의 `song0328/last-safe-fuel-mvp` 브랜치를 받고 `docs/handoff.md`, 설계 문서, 구현 계획을 순서대로 읽은 뒤 Task 4 Repository 계약과 합성 adapter부터 TDD로 이어서 작업해 줘. UI는 다크 허니 골드와 내비 위 팝업·위젯 방향이며 사용자 승인 전 세부 형태와 범위를 임의로 확정하지 마.

## 관련 문서

- `docs/superpowers/specs/2026-11-10-fuel-pilot-mvp-design.md`
- `docs/superpowers/plans/2026-11-10-fuel-pilot-mvp.md`
- `docs/architecture.md`
- `AGENTS.md`
