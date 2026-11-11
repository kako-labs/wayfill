# FUEL PILOT 승인 UI 통합 설계

## 의도

회사 PC에서 만든 판단 엔진·Android 골격과 집 PC에서 승인한 뽀동이 UI를 하나의 제품 브랜치로 합친다. 사용자가 승인한 화면과 모션은 유지하되, 데모 데이터와 가짜 지도는 실제 앱 코드로 가져오지 않고 기존 판단 엔진과 차량·내비 어댑터가 UI에 값을 공급하게 한다.

## 기준선

- 제품 코드 기준: `song0328/last-safe-fuel-mvp`의 `1ab7659`
- 승인 UI 전달 기준: `ui/podong-delivery-20261110`의 `03b2486`
- 승인 UI 코드 기준: `95d0877`
- 고정 태그: `podong-ui-approved-2026-11-10`
- 통합 브랜치: `integrate/podong-approved`
- 공통 기반: `main`의 `8c8f6ca`

## 목표

- 승인된 웹 기준 화면과 72프레임 뽀동이 모션을 보존한다.
- Compose UI 소스와 리소스를 기존 `:feature:assist` 모듈에 통합한다.
- `FuelDecision`을 UI 표시 모델로 변환하는 명시적 어댑터를 둔다.
- 실제 차량 상태 전에는 `Motion.Unknown`을 유지한다.
- 실제 내비 SDK 성공·실패 응답으로만 경유지 추가 상태를 확정한다.
- 주유 완료 이벤트 ID와 사용자 모션 설정을 재생성·재시작에도 보존한다.
- 데스크톱 웹, Kotlin 상태 로직, Android 모듈, 전체 앱 검증을 구분해 실행한다.
- 완료 시 브랜치를 푸시하고 원격 커밋 해시까지 재확인한다.

## 비목표

- 실제 Pleos SDK가 없는 환경에서 차량·내비 연동 성공을 가장하지 않는다.
- 앱 내부에 별도 지도를 구현하거나 웹 데모를 WebView로 감싸지 않는다.
- 승인된 캐릭터, 색, 문구 흐름을 다시 디자인하지 않는다.
- 스프라이트 모션을 벡터·60fps 애니메이션으로 다시 만들지 않는다.
- `main`에 직접 커밋하거나 검증되지 않은 빌드 산출물을 배포하지 않는다.

## 피해야 할 결과

- 데모의 42L, 610km 같은 예시 숫자가 운영 기본값으로 들어가는 것
- `Unknown` 차량 상태를 정차 상태처럼 취급해 큰 동작을 재생하는 것
- 같은 주유 이벤트가 화면 재생성 또는 앱 재시작 후 다시 재생되는 것
- 내비 요청 직후 성공으로 표시하고 실제 SDK 응답을 무시하는 것
- UI 세션과 코드 세션이 같은 파일을 동시에 수정해 승인 기준이 갈라지는 것
- 로컬 커밋만 만든 뒤 원격 보존을 확인하지 않고 완료로 보고하는 것

## 검토한 통합 방식

### 1. 두 브랜치를 병합한 뒤 제품 구조로 정리 — 선택

코드 브랜치에서 UI 브랜치를 병합해 전체 이력과 승인 자료를 보존한다. 충돌하는 `.gitignore`, `AGENTS.md`, `README.md`는 두 지침을 합쳐 해결한다. 이후 중첩 Android 프로젝트의 UI 소스·테스트·리소스를 기존 `:feature:assist`로 옮기고 중복 Gradle 래퍼와 AAR은 제품 트리에서 제거한다. 승인 태그에 원본 전달본이 남으므로 이력과 배포 증거는 보존된다.

장점은 승인 근거, 웹 기준 화면, 소스 이력을 모두 유지한다는 점이다. 단점은 병합 직후 중복 Android 구조를 한 차례 정리해야 한다는 점이다.

### 2. 필요한 UI 파일만 선택 복사

충돌과 저장소 크기는 줄지만 어떤 파일이 승인 세트인지 다시 판단해야 하고, 집 PC 작업의 커밋 이력과 검증 자료가 분리된다. 향후 UI 세션이 기준 커밋을 추적하기 어려워 선택하지 않는다.

### 3. 빌드된 AAR만 사용

빠르게 화면을 붙일 수 있지만 상태 모델과 앱 아키텍처를 조정하기 어렵고, Compose 의존성 및 디버깅 책임이 불명확해진다. 현재 제품 개발 단계에는 적합하지 않다.

## 목표 구조

```text
app
  -> feature:assist
       -> core:model
       -> data:energy / data:location / data:poi / data:navigation

feature:assist
  - 승인 Compose 화면과 뽀동이 리소스
  - FuelUiState reducer 및 ViewModel 상태 소유
  - FuelDecision -> FuelRouteUi 변환 어댑터
  - 내비 요청 콜백과 성공/실패 이벤트 연결점

core:model
  - 순수·결정론적 FuelDecisionEngine
  - UI/Android 의존성 없음
```

웹 데모와 승인 문서는 `web/`, `docs/`에 남겨 시각적 회귀 기준으로 사용한다. `reference/`는 원본 출처 확인용으로 유지한다. 중첩 `android/` 빌드와 `release-artifacts/` AAR은 `:feature:assist` 이전과 검증 후 제거한다.

## 상태 연결

- `FuelDecision.NoAction`: 적극적인 추천 화면을 숨기고 조용한 기본/위젯 상태를 사용한다.
- `FuelDecision.Monitoring`: 마지막 안전 주유소 정보를 보존하되 경고 CTA는 아직 노출하지 않는다.
- `FuelDecision.Recommendation`: `primary`를 `recommended`, `alternatives`를 비교 목록으로 변환하고 엔진의 추천 이유 문구를 전달한다.
- `FuelDecision.Unavailable`: 임의 추천을 만들지 않고 데이터 확인 중 또는 사용 불가 상태를 표시한다.
- `SignalStatus.STALE/UNAVAILABLE`: UI가 임의 수치를 만들지 않도록 null과 명시적 상태로 전달한다.
- 주행/정차/미확인 상태, 주유 완료 이벤트, 내비 결과는 판단 결과와 별도의 실제 이벤트 흐름으로 유지한다.

정확한 `Screen` 전이는 ViewModel이 소유한다. reducer는 동일 이벤트 억제, 주행 전환 시 큰 동작 중단, 내비 요청 중복 방지를 계속 담당한다.

## 병합과 문서 정책

- `.gitignore`: 두 브랜치 항목의 합집합을 사용한다.
- `AGENTS.md`: 제품 아키텍처 규칙과 승인 UI 유지·검증 규칙을 모두 보존한다.
- `README.md`: 제품 설명을 첫 진입점으로 두고 승인 UI 데모와 인계 문서를 하위 링크로 연결한다.
- `song0328/data-contracts`는 `last-safe-fuel-mvp`와 트리가 같고 마지막 커밋만 달라 별도로 병합하지 않는다.
- 회사의 과거 UI 대화는 승인 UI 브랜치의 `reference/claude-export.html`과 출처 문서에 흡수됐으므로 별도 소스 브랜치로 취급하지 않는다.

## 병렬 작업 원칙

Orca CLI를 이 저장소 컨텍스트에서 사용한다.

- 읽기 전용 설계 검토, 테스트 분석, UI/도메인 매핑 검토는 같은 폴더의 별도 Orca 세션에 맡길 수 있다.
- 파일을 수정하는 병렬 작업은 각자 별도 Orca 작업 트리에서 수행한다.
- 한 통합 담당자가 결과를 순서대로 가져오고 충돌을 해결한다.
- 같은 파일을 두 세션이 동시에 수정하지 않는다.
- 각 작업은 커밋과 검증 결과를 남기며, 통합 담당자가 독립적으로 diff와 테스트를 확인한다.

## 검증

- Python 아키텍처 정책 테스트
- `:core:model:test`
- `:feature:assist:testDebugUnitTest`
- `:feature:assist:lintDebug`
- `:app:assembleDebug`
- 루트 `verifyAll`
- 웹 `npm test`, `npm run verify`
- 추천·경유지 요청·내비 성공/실패
- 동일 주유 이벤트 재전달, 화면 재생성, 프로세스 재시작
- Moving/Unknown 전환 시 큰 동작 즉시 중단
- 데이터 누락 시 임의 수치 미표시
- 좁은 화면과 모션 감소 설정
- GitHub 원격 브랜치 해시와 로컬 HEAD 일치

회사 PC의 공용 Gradle 캐시 오류와 격리 캐시의 플러그인 다운로드 실패는 코드 실패와 분리해 기록한다. 검증이 환경 때문에 막히면 통과로 표현하지 않고 실행된 범위와 미검증 범위를 명시한다.

## 완료 기준

- 승인 UI가 `:feature:assist`에서 빌드되고 앱이 이를 사용한다.
- 판단 엔진과 UI 사이 어댑터 단위 테스트가 있다.
- 데모 데이터가 실제 앱 실행 경로에 없다.
- 병합 충돌과 중복 Android 빌드 구조가 정리됐다.
- 가능한 전체 검증이 통과하고 환경 제한은 별도 기록됐다.
- 통합 브랜치가 원격에 푸시됐고 원격 해시가 확인됐다.
