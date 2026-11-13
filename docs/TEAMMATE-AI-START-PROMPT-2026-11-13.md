# WAYFILL 팀원 AI 시작 지시문

아래 블록을 팀원이 사용하는 코딩 AI에 그대로 전달한다. 이 문서는 작업을 대신 완료했다는 증거가 아니다. 실제 PLEOS SDK와 권한과 기기 검증은 팀원이 제공하고 확인해야 한다.

```text
이 저장소는 현대차 앱 공모전 일반 부문 출품작 WAYFILL입니다.
팀명은 KA&KO이고 대표자 포함 2명입니다.

feature/prepleos-hardening-20261113 브랜치의 최신 커밋에서 작업하세요.
작업을 시작하기 전에 AGENTS.md와 START-HERE.md와 docs/APPROVED-BASELINE.md와 docs/APP-SESSION-HANDOFF.md와 docs/ANDROID-INTEGRATION.md와 docs/TEAMMATE-PLEOS-HANDOFF-2026-11-13.md를 전부 읽으세요.

목표는 이미 완성된 WAYFILL의 UI와 순수 결정 엔진을 보존하면서 실제 PLEOS 차량 데이터와 내비게이션과 HUN과 가능한 경우 Gleo 음성 그리고 주유 완료 이벤트를 연결하는 것입니다.

반드시 지킬 조건입니다.
- 제품 이름과 화면 표기는 WAYFILL로 유지하세요.
- 결정 엔진은 순수하고 결정적으로 유지하세요.
- 실제 SDK나 권한이나 데이터가 없으면 Unsupported 또는 Unavailable을 반환하세요.
- 실제 빌드에서 합성 데이터나 시연 숫자를 대체 입력으로 사용하지 마세요.
- 앱 안에 지도를 추가하지 마세요. 지도 렌더링은 차량 내비게이션의 책임입니다.
- 내비 요청이 실제로 성공하기 전에 성공 화면을 표시하지 마세요.
- 추천 key와 station ID와 route ID와 검증된 WGS84 좌표를 요청부터 HUN과 음성과 내비 응답까지 유지하세요.
- 주유 완료는 안정적인 공급자 event ID로만 확정하세요. 주행 가능 거리 증가만으로 주유를 추정하지 마세요.
- 주행 또는 차량 상태 Unknown 전환 시 주유 애니메이션과 위험한 상호작용을 즉시 취소하세요.
- PLEOS SDK 바이너리와 API 키와 local.properties와 개인 경로와 기기 식별자는 커밋하지 마세요.
- 기존 뽀동이 16프레임 승인본과 정차 연기와 주행 중 calm 표현과 reduced-motion 동작을 바꾸지 마세요.

다음 순서로 작업하세요.
1. scripts/pleos_probe.py로 사용 가능한 SDK와 권한과 차량 데이터와 내비 서비스와 HUN과 음성 기능을 실제 환경에서 조사하고 결과를 기록하세요.
2. 차량 정차·주행·Unknown과 주행 가능 거리와 활성 목적지·route ID를 기존 port에 연결하세요.
3. 실제 주유소 검색 결과의 좌표와 경로상 거리와 우회 시간과 영업 상태를 기존 후보 모델에 연결하세요.
4. NaviHelper의 경유지 추가 또는 목적지 설정을 기존 NavigationRepository에 연결하고 실제 성공·실패·취소·시간 초과를 그대로 반환하세요.
5. 백그라운드 추천은 기존 HUN 정책과 동일한 추천 identity를 사용하고 바로 안내와 나중에 두 동작과 자동 만료를 검증하세요.
6. 호스트가 공식 음성 action을 제공할 때만 Gleo 명령을 recommendationVoiceCommand에 연결하세요. 일반 TTS 성공을 Gleo 연동 성공으로 기록하지 마세요.
7. 안정적인 주유 완료 provider event와 event ID가 있을 때만 연결하세요.
8. docs/TEAMMATE-PLEOS-HANDOFF-2026-11-13.md의 필수 시나리오를 실제 PLEOS 화면에서 검증하세요.
9. python scripts/dev.py doctor와 python scripts/dev.py verify를 실행하고 실패를 생략하지 마세요.
10. 실제 commit SHA와 전체 로그와 기기 정보와 화면 캡처와 오디오 캡처와 설치 APK의 SHA-256을 docs/SESSION-PROGRESS.md에 기록하세요.

각 기능은 구현 상태와 실제 호스트 연결 상태와 실기 검증 상태를 따로 보고하세요. SDK나 권한이 없어 검증하지 못한 항목은 완료라고 쓰지 말고 정확한 blocker와 다음 사람이 해야 할 일을 남기세요.

PLEOS 실기 연결과 검증이 끝난 뒤에만 docs/WAYFILL-VIDEO-PRODUCTION-PLAN.md와 docs/WAYFILL-VIDEO-STORYBOARD.md와 docs/wayfill-scenes.plan.ts를 기준으로 영상을 구성하세요. 시뮬레이터 화면은 흐름 설계 참고용이며 실기 장면인 것처럼 쓰지 마세요. 자막은 계획된 한 줄 문장을 유지하고 실제 동작과 다른 자막은 수정하세요. 최종 영상에는 정차 홈과 주행 중 작은 창과 HUN과 내비 반영과 주유 완료와 기록 화면이 순서대로 보여야 합니다.

기존 변경을 덮어쓰거나 main에 직접 커밋하지 말고 별도 feature branch에서 작은 단위로 커밋하세요. 구현 전에 현재 코드와 문서를 먼저 확인하고 이미 있는 port와 테스트를 재사용하세요.
```

## 팀원이 직접 제공하거나 확인할 것

- PLEOS SDK 접근 권한과 저장소 인증
- 실제 PLEOS 호스트 또는 공식 에뮬레이터와 기기 serial
- 차량·위치·내비·알림·음성 권한 승인
- HUN 허용 정책과 공모전 배포 환경 확인
- 실제 음성 호출과 내비 경로 반영과 주유 이벤트의 육안 확인
- 영상용 실기 화면 녹화와 차량 상태가 드러나는 장면 선택
- 최종 APK 서명과 제출 계정 작업

AI는 코드와 테스트와 편집안을 만들 수 있지만 위 항목을 추정해서 통과 처리해서는 안 된다.
