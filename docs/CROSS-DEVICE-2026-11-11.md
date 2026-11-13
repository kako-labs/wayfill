# 다른 노트북에서 이어가기 · 2026-11-11 고정 인계

## 정확한 저장 위치

- 저장소: https://github.com/kako-labs/wayfill (비공개)
- 현재 개발 브랜치: `integrate/podong-approved`
- 이번 전체 인계 태그: `fuel-pilot-handoff-2026-11-11`
- 영구 다운로드: https://github.com/kako-labs/wayfill/releases/tag/fuel-pilot-handoff-2026-11-11
- 작업/검증 PR: https://github.com/kako-labs/wayfill/pull/2
- 실제 앱 코드 검증 커밋: `0b8a725f2e4f02e8e743f26dc4c65665fc897fd6`
- 해당 push CI: https://github.com/kako-labs/wayfill/actions/runs/43027203547
- 해당 PR CI: https://github.com/kako-labs/wayfill/actions/runs/43027213434

저장소 기본 화면의 `main`은 초기 커밋이다. 최신 앱이 없다고 판단하지 말고 위 브랜치 또는 고정 Release를 연다. 11월 10일 `podong-ui-approved-2026-11-10`은 승인 UI 원본이며 이번 전체 앱 인계본과 구분한다. 기존 승인 태그는 변경하지 않았다.

## 새로 받기

```powershell
git clone --branch integrate/podong-approved https://github.com/kako-labs/wayfill.git fuel-pilot
cd fuel-pilot
git status
git log -5 --oneline
```

기존 폴더를 쓰면 먼저 `git status`로 회사에서 작업한 변경을 확인하고 보존한다. 이후 `git fetch origin --tags`, `git switch integrate/podong-approved`, `git pull --ff-only origin integrate/podong-approved`를 사용한다. 브랜치가 아직 없으면 `git switch --track origin/integrate/podong-approved`로 만든다. 로컬 변경을 강제로 지우거나 덮어쓰지 않는다.

고정 인계본 자체가 필요하면 Release의 소스 ZIP을 받거나 태그를 별도 폴더로 clone한다. 태그 checkout에서 개발할 때는 새 작업 브랜치를 만든다.

```powershell
git clone --branch fuel-pilot-handoff-2026-11-11 https://github.com/kako-labs/wayfill.git fuel-pilot-20261111
cd fuel-pilot-20261111
git switch -c resume/fuel-pilot
```

404 또는 검색 실패 시 저장소가 없다고 단정하지 않는다. 이 저장소에 권한이 있는 GitHub 계정으로 로그인한 뒤 `gh repo view kako-labs/wayfill` 또는 위 주소를 직접 확인한다. 브라우저/연결 도구/CLI 계정이 서로 다를 수 있다. 비밀번호·토큰을 채팅이나 소스에 붙여넣지 않는다.

## 두 세션 공통으로 읽을 것

1. 이 문서와 [CONTINUE-HERE](../CONTINUE-HERE.md), [제품 준비 결과](product-readiness-2026-11-11.md).
2. UI 세션: [UI-SESSION-HANDOFF](UI-SESSION-HANDOFF.md), [승인 기준](APPROVED-BASELINE.md). `web/index.html`과 `web/animation-lab.html`은 압축을 푼 뒤 로컬 브라우저에서 연다.
3. 앱 개발 세션: [APP-SESSION-HANDOFF](APP-SESSION-HANDOFF.md), [ANDROID-INTEGRATION](ANDROID-INTEGRATION.md), [실제 데이터 경계](data-provider.md). 실제 호스트 코드는 `app`, 승인 Compose UI/상태 처리는 `feature/assist`에 있다.
4. 제출 자료 초안은 [submission](submission/README.md). 작업 경과는 [SESSION-PROGRESS](SESSION-PROGRESS.md).

## 구현·검증 상태

홈·정차 전용 여유거리 설정, Application 공유 런타임, 권한을 거치는 감시/중지/복원, 목적지/경유지 구분, 실제 도로 거리 기반 판단, 주유소 서버 경계, 별도 debug 시연, CI/소스 전달 도구를 구현했다. 승인된 뽀동이 72프레임과 정차/주행/중복 이벤트/모션 감소 정책을 보존했다.

위 `0b8a725`의 두 CI 실행에서 verify/compose-ui 모두 성공했다. 내려받은 보고서의 Kotlin 단위111개와 Android35 화면12개는 실패·오류·건너뜀 모두0개다. Python 도구12개, 서버9개, 웹17개도 검사했다. ZIP의 Unix wrapper 줄바꿈·실행 권한, APK/ZIP 체크섬과 소스 파일을 확인했다. 실제 프로세스 종료/서비스 복원은 Android35 에뮬레이터에서 확인했다. 이번 추가 변경은 인계 문서뿐이므로 앱 전체 검사를 반복하지 않았다. 문서 커밋의 CI 생략은 새 테스트 통과로 세지 않는다.

실제 Pleos 차량/내비/표시 권한, API 승인·키·HTTPS 배포, 검증된 좌표 변환과 경로별 주유소 수집/도로 계산은 남아 있다. 운영 앱은 미지원 환경에서 시연 값으로 대체하지 않는다. 화면·애니메이션의 체감은 사람이 직접 봐야 하며 실제 Pleos 차량 기기 검증은 실행하지 않았다.

## Release 파일

- `fuel-pilot-handoff-20261111-source.zip`: 현재 앱/웹 UI/모션/서버/테스트/인계 문서 소스. SDK·키·캐시·local.properties는 포함하지 않는다.
- `app-debug.apk`: 검사가 끝난 CI의 설치 가능한 검토 빌드. 일반 시작은 미지원 상태가 정상이고 홈에서 별도 시연에 들어간다.
- `app-release-unsigned.apk`: 시연 코드 제외를 확인한 미서명 빌드. 설치/배포 서명은 별도다.
- `SHA256SUMS.txt`: 위 파일의 무결성 확인값.

JDK17/Android SDK35와 필요한 네트워크 의존성은 새 노트북에서 설정한다. 소스 ZIP에는 `.git`이 없으므로 빌드/검사는 가능하지만 이후 commit/push할 개발 폴더는 Git clone으로 준비한다.

## 다음 세션에 그대로 붙여넣기

> kako-labs/wayfill의 integrate/podong-approved 브랜치에서 이어가 줘. 기본 main에는 최신 앱이 없으니 그걸 보고 없다고 하지 마. fuel-pilot-handoff-2026-11-11 Release와 docs/CROSS-DEVICE-2026-11-11.md, CONTINUE-HERE.md를 먼저 읽어. UI 세션과 앱 개발 세션 인계 문서를 함께 확인하고 회사 노트북의 기존 변경을 보존해. 승인된 72프레임 뽀동이와 상태 처리를 유지하고 실제 Pleos/주유소/경로 데이터 연결을 이어가. 테스트 통과와 사람의 화면·실차 검증을 구분해서 기록해.
