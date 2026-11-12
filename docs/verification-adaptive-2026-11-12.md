# 작은 창·백그라운드 안내 검증 · 2026-11-12

대상: `ui/adaptive-companion-20261112`, 시작 `94d0c7d`. 실제 PLEOS 장치가 아닌 Windows 호스트와 일반 Android API 35 에뮬레이터에서 확인했다. 기기/캐시의 개인 경로는 소스 설정에 넣지 않았다.

## 실행한 검사

| 검사 | 결과 |
| --- | --- |
| `python scripts/dev.py doctor` | JDK 17/Android SDK 확인 통과. 환경 변수는 실행 프로세스에서만 설정 |
| `python scripts/dev.py verify` | 전체 모듈 빌드·architecture·lint·단위 테스트 통과. XML 합계 **155개, 실패 0, 오류 0, 생략 0** |
| 마지막 Manifest 변경 뒤 `:app:assembleDebug :app:lintDebug` | 통과. resizeableActivity/distractionOptimized 포함 APK 생성 |
| `:app:assembleDebugAndroidTest` | 통과 |
| Android instrumentation 관련 4개 클래스 | **13개 통과**, 실패 0. SplitWindowUiTest 3, VehicleScaleUiTest 5, NativeDemoTest 4, SettingsMotionTest 1 |
| 마지막 한글 줄바꿈 보정 뒤 작은 창 재검사 | **3개 재통과**. 위 13개에 더해 16개로 세지 않음. 최신 네이티브 캡처에서 ‘연료’, ‘서울방향’이 단어 중간에서 갈리지 않는 것을 직접 확인 |
| `npm test` | 18개 통과, 생략 0. 이번 웹 비교 UI 전용 자동 회귀 테스트라는 뜻은 아님 |
| `npm run verify` | 아틀라스 일치·패키지·새 웹 비교 페이지 3개 포함 오프라인 의존성 검사 통과 |
| `node --check web/companion-preview.js` | 통과 |
| `python -m unittest discover -s scripts/tests -q` | 23개 통과 |
| `git diff --check` | 통과 |

전체 테스트는 검사 중 발견한 실제 실패를 수정한 경우에만 다시 돌렸다. 마지막 표시 문구 줄바꿈 보정은 관련 feature 테스트/앱 빌드·lint/작은 창 UI 검사만 다시 확인한다. Gradle 준비 작업의 SKIPPED/NO-SOURCE를 통과한 테스트 수에 넣지 않았다.

## 실제로 확인한 화면과 흐름

- 420×620dp의 주행/정차 추천, 640×340dp의 짧은 주행 창. 긴 주유소 이름을 넣어 버튼이 겹치지 않고 창 안에 완전히 들어오는지와 안내 버튼 최소 76dp를 검사했다.
- 현재 추천 키·주유소 ID가 클릭 의도로 그대로 전달됨. 주행 중 두 행동만 존재하고 Unknown에서는 행동이 사라짐.
- 정차 상세/설정에서 출발하면 닫힘. 출발 후 최신 정보를 다시 읽고 정차 시 홈으로 복귀함.
- 내비 성공/실패/근처 모드, 회전 재생성, 중복 주유 완료 이벤트와 큰 동작 취소.
- 모션 감소 설정에서 캐릭터가 깜박임 주기를 지나도 정지 상태를 유지함.
- 일반 Android 시스템의 언어와 무관한 한글 어절 보존을 위해 표시용 Hangul 사이에 word joiner를 넣고 접근성에는 원문을 제공함.

캡처:

- [좁은 주행 창](screenshots/native-split-420x620-Moving.png)
- [좁은 정차 창](screenshots/native-split-420x620-Parked.png)
- [짧은 주행 창](screenshots/native-split-640x340-Moving.png)
- [웹의 내비 옆 시안](screenshots/split-window-preview-20261112.png)
- [웹 알림 시안](screenshots/notification-preview-20261112.png)

웹은 ego-browser로 확인했다. 1440px/420px 브라우저 폭에서 가로 넘침 없음, 76px 버튼, 미루기 후 표면 변경 시 재노출 없음, 내비 응답 전 성공 미표시, 실패 후 재시도, Unknown/오래된 정보의 행동 차단, 15초 무응답 종료를 확인했다. HTML 알림 배치·크기는 실제 PLEOS HUN 모양을 증명하지 않는다.

## 발견하고 수정한 실패

- 원래 reducer가 출발 시 정차 추천 노출을 지운 직후 같은 presentation을 다시 노출했다. 이전 presentation을 무효화하고, coordinator가 motion 변화 때 즉시 새 평가를 요청하게 수정했다. 주기 타이머까지 기다리지 않는 단위 테스트를 추가했다.
- 앱 헤더의 Compose 암시적 수신자, 알림 기록 Set의 takeLast, instrumentation의 잘못된 import 때문에 최신 원본이 컴파일되지 않던 곳을 수정했다.
- SDK 어댑터의 custom permission/비동기 callback을 lint가 추적하지 못했다. 포트 진입 시 권한을 다시 검사하고 SecurityException → Unsupported 및 best-effort cleanup을 유지하며 해당 메서드에만 lint 사유를 명시했다.
- 마지막 앱 lint에서 발견한 사용하지 않는 예전 알림 문자열·권한 이름 상수의 API 검사·Boolean commit 결과가 필요한 저장 함수의 KTX 권고도 정리했다.
- 첫 관련 기기 시험 12개 중 2개 실패를 성공에 넣지 않았다. 출발 후 새 평가 지연을 수정하고, 정차 후 준비 완료 상태를 기다리도록 시험을 보강한 뒤 13개가 통과했다.

## 미실행·남은 확인

- 앱 instrumentation 전체 20개 중 이번 변경과 관련된 13개를 실행했다. 나머지 **7개는 미실행**: MonitoringRuntimeTest 4, ProductionStartupTest 1, JsonGatewayDecoderDeviceTest 2. 통과 수에 포함하지 않았다. 기존 운영 호스트 시험 일부는 Unknown에서도 홈/설정을 노출하거나 모니터링 시작을 허용하던 이전 기대값을 사용한다.
- 실제 PLEOS 에뮬레이터/Devbox/실차, 실제 OS HUN의 버튼·음성·잘림·다중 창 크기와 정책, 차량 신호/주유 완료 스트림과 실내비 성공은 미검증이다.
- `distractionOptimized`는 앱 선언이며 플랫폼의 승인이나 운전자 주의분산 검증 결과가 아니다. 기존 gateway의 검증된 좌표·도로 거리·NavigationTarget 연결도 여전히 별도다.
- 큰 시스템 글꼴, 아주 작은 창, 실제 사용자의 읽기·터치 편의는 추가 확인이 필요하다. 자동 검사와 정지 캡처만으로 시각 완성을 주장하지 않으며 사람이 실제 크기로 직접 봐야 한다. 소리는 변경하지 않았다.
