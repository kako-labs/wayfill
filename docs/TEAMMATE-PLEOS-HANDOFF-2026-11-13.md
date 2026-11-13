# WAYFILL 팀원 PLEOS 연결 인계

팀원이 코딩 AI에 바로 작업을 맡길 때는 [TEAMMATE-AI-START-PROMPT-2026-11-13.md](TEAMMATE-AI-START-PROMPT-2026-11-13.md)의 지시문을 먼저 그대로 전달한다.

## 고정 정보

- 앱명: **WAYFILL**
- 팀명: **KA&KO**
- 대표자: 접수양식 01에 기재(저장소에는 실명을 적지 않음)
- 참가 인원·부문: **2명 · 일반 부문**
- 인계 브랜치: `feature/prepleos-hardening-20261113`
- 앱 버전: `0.2.0-prepleos` / code 2
- 실제 연결 전 기준: [PRE-PLEOS-READINESS-2026-11-13.md](PRE-PLEOS-READINESS-2026-11-13.md)

연락처와 서명은 이 저장소에 넣지 않는다. 저장소의 웹 시뮬레이터와 debug 시연은 합성 입력이며 실제 PLEOS 검증 증거가 아니다.

## 받기

```powershell
git clone --branch feature/prepleos-hardening-20261113 --single-branch https://github.com/kako-labs/wayfill.git wayfill
cd wayfill
git rev-parse HEAD
python scripts/dev.py doctor
```

다른 작업이 있는 저장소에 덮어쓰지 않는다. 기존 변경을 보존하고 별도 clone 또는 worktree에서 시작한다.

먼저 다음 문서를 순서대로 읽는다.

1. `AGENTS.md`
2. `CONTINUE-HERE.md`
3. `docs/APP-SESSION-HANDOFF.md`
4. `docs/ANDROID-INTEGRATION.md`
5. `docs/data-provider.md`

## 현재 준비된 것

- 정차 전체 화면과 주행 작은 창에 대응하는 Compose UI
- 주행·Unknown·모션 감소에서 정지하는 뽀동이와 정차 중 1회 표현 모션
- 웹·Android가 공유하는 calm + 추천·소량·충분한 주유·저연료 회복 32포즈 시트와 시간표
- 순수 결정 엔진과 명시적 Unsupported/Unavailable 상태
- 추천 key·station ID·검증된 WGS84 내비 타깃을 보존하는 경유지 요청 계약
- 실제 내비 성공 응답 전에는 성공을 표시하지 않는 요청 흐름
- 시스템 HUN 프로토타입과 버튼 액션의 동일 추천 identity 검증
- 실제 호스트가 연결할 `RecommendationSpeechPort`와 Gleo 음성 액션 변환
- 중복 주유 이벤트 억제와 출발·Unknown 시 큰 모션 취소
- 합성 입력이 debug 런처에만 존재하고 release에서 거절되는 구조
- PLEOS 흐름 검토용 `web/pleos-scenario-simulator.html`

## 실제 PLEOS에서 연결할 순서

1. `scripts/pleos_probe.py --serial <target>`로 대상·사용자·권한·차량 서비스·내비 서비스를 읽기 전용으로 기록한다.
2. 실제 차량의 정차·주행·Unknown과 주행 가능 거리 입력을 포트에 연결한다. 값을 못 읽으면 Unsupported/Unavailable을 유지한다.
3. 활성 목적지와 경로 ID를 연결한다. 경로가 없다는 상태와 내비 미지원 상태를 구분한다.
4. 실제 주유소 공급자와 연료 종류를 연결한다. 검증된 좌표와 도로 거리·우회 시간이 없는 후보는 경유지 요청에 사용하지 않는다.
5. NaviHelper 경유지·목적지 요청을 연결한다. 요청 ID·추천 key·station ID·route ID를 유지하고 실제 성공·실패·취소·시간 초과를 되돌려준다.
6. PLEOS 멀티 윈도우에서 정차 전체 화면과 내비 옆 작은 창을 실제로 확인한다. `D` 전환이 창을 강제로 숨긴다고 가정하지 않는다.
7. 앱이 화면에서 밀려난 백그라운드 상태에서 시스템 HUN을 게시한다. 두 버튼과 자동 만료와 오래된 액션 거절을 확인한다.
8. `RecommendationSpeechPort`를 호스트 TTS·오디오 포커스 정책에 연결한다. 내비 음성·통화 중에는 호스트 정책에 따라 억제한다.
9. Gleo가 실제 앱 액션을 전달할 수 있을 때만 `recommendationVoiceCommand`를 연결한다. 단순 음성 인식 성공을 WAYFILL 동작 성공으로 기록하지 않는다.
10. 신뢰할 수 있는 주유 완료 이벤트와 안정적인 event ID를 연결한다. 단순 range 증가만으로 주유를 추측하지 않는다.

## 반드시 확인할 시나리오

- 정차 홈에서 목적지·주행 가능 거리·추천 이유가 실제 값으로 표시된다.
- 작은 창에서는 남은 거리와 핵심 상태만 보이며 큰 캐릭터 동작이 없다.
- WAYFILL이 화면에서 밀려나도 허용된 모니터링이 유지되고 HUN이 한 번 나타난다.
- HUN의 `바로 안내`가 정확한 추천 주유소를 내비에 요청한다.
- 내비 성공 응답 전에는 WAYFILL이 성공으로 표시하지 않는다.
- `나중에`와 자동 만료와 15분 쿨다운이 동작한다.
- 같은 주유 이벤트가 재전달돼도 주유 인사와 기록이 반복되지 않는다.
- 주유 인사 중 출발하거나 차량 상태가 Unknown이 되면 큰 동작이 즉시 멈춘다.
- 위치·연료·좌표·도로 거리·권한·SDK 중 하나가 없으면 합성 값 없이 실패 상태가 보인다.
- 앱 종료와 백그라운드 전환을 구분한다. 명시적으로 종료된 앱이 계속 동작한다고 주장하지 않는다.

## 팀원이 돌릴 명령

```powershell
python scripts/dev.py doctor
python scripts/dev.py verify
npm.cmd test
npm.cmd run verify
python -m unittest discover -s scripts/tests -v
python -m unittest discover -s server/tests -v
python scripts/pleos_e2e.py status --serial <target>
```

`python scripts/dev.py verify`가 저장소 인증서나 SDK 배포 문제로 실패하면 통과로 기록하지 않는다. 실패 원문과 환경을 보존한다.

## 팀원이 돌려줄 증거

- 작업 브랜치와 전체 commit SHA
- `doctor`와 `verify` 전체 로그
- `pleos_probe`와 실행한 `pleos_e2e` 결과
- 정차 전체 화면·내비 옆 작은 창·HUN·내비 경로 반영·주유 완료 화면 캡처
- HUN 음성 또는 Gleo를 포함했다면 실제 오디오·액션 연결 녹화
- 실제 설치한 APK와 SHA-256
- 실제 기기/에뮬레이터 버전·해상도·권한·미확인 항목
- `docs/SESSION-PROGRESS.md`에 구현한 어댑터와 남은 제한 기록

## 금지 사항

- 운영 빌드에서 시연 숫자나 합성 주유소로 실제 입력을 대신하지 않는다.
- 앱 안에 지도를 추가하지 않는다.
- 내비 성공 응답 전에 경로 반영 성공을 표시하지 않는다.
- 임의 좌표나 공급자 직선 거리를 검증된 경유지로 넘기지 않는다.
- 시스템이 허용하는 HUN 위치와 멀티 윈도우 배치를 앱이 강제한다고 표현하지 않는다.
- Gleo와 TTS가 실제로 연결되기 전에 영상이나 문서에서 구현 완료로 쓰지 않는다.
- SDK 바이너리·키·`local.properties`·개인 경로·기기 식별자를 커밋하지 않는다.

## 영상으로 돌려줄 화면

최종 영상 순서와 한 줄 자막은 [WAYFILL-VIDEO-PRODUCTION-PLAN.md](WAYFILL-VIDEO-PRODUCTION-PLAN.md)와 [wayfill-scenes.plan.ts](wayfill-scenes.plan.ts)를 따른다. 실제 화면에 없는 기능은 대본에서 빼고 브라우저 시뮬레이터 컷에는 `PLEOS 화면 시뮬레이션`을 표시한다.
