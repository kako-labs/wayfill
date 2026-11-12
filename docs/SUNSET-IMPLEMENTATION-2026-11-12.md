# 소프트 선셋 구현

> 이 문서는 11월 12일 정지 자산의 최초 적용 기록이다. 11월 13일에 새 64개 원본 셀의 모션, 실제 기록/설정 메뉴, 상태 화면을 추가했다. 현재 구현과 검증은 [최신 제품 인계](PRE-PLEOS-READINESS-2026-11-13.md)를 우선한다. 아래 미완료 목록과 숫자는 당시의 상태다.

사용자가 선택한 홈과 도로 띠 뽀동이는 [승인 원본](../design/approved-sunset/README.md)에 보존했다. 기존 네이비 재작업보다 이 선택이 우선한다. 시안을 통째로 배경에 깔지 않고 배경/투명 캐릭터/실제 글자/실제 버튼으로 분리했다.

## 적용

- 웹 `companion-preview.html`은 기존 링크 그대로 새 석양 홈으로 시작한다. `sunset-theme.css`와 `assets/sunset/`을 사용한다. 거리/주유소/상태/행동은 DOM이다. 내비 요청 시 성공을 선표시하지 않고 시연 응답을 기다린다.
- Android `AppShell`은 석양 배경과 하단 실제 홈/주유 안내/설정 메뉴를 사용한다. 정차 홈은 위쪽 메시지·거리·캐릭터, 아래쪽 주유소·행동 패널이다. 모니터링과 권한 재확인 기능은 아래 스크롤 영역에 유지한다.
- 내비 옆 좁은 창과 낮은 창은 같은 색과 캐릭터를 사용한다. 배경을 어둡게 해 가독성을 보완하고 76dp 이상 행동 버튼을 유지한다. Unknown/오래된 정보는 안내 행동을 제공하지 않는다.
- 모든 Android 제품 캐릭터 표시는 새 정지 이미지로 통일했다. 기존 이벤트 타이밍/취소/중복 억제 계약은 유지하되, 새 외형에 기존 캐릭터 프레임을 섞지 않는다. 정지 표시에는 주기적 60fps 갱신을 하지 않는다.
- 웹의 기록/설정 메뉴는 시안 범위를 설명하는 상태 문구를 표시한다. 기록 화면은 새로 구현하지 않았으며 실제 기록을 꾸며내지 않는다. Android 메뉴도 실제 연결된 세 메뉴만 제공한다.
- 시스템 알림은 OS가 그린다. HTML은 내용 시연이며 외형이나 위치를 보장하지 않는다. 알림 로직은 이 디자인 변경에서 수정하지 않았다.
- `tools/verify-package.cjs`는 새 배경/캐릭터의 웹·Android PNG가 바이트 단위로 같은지 검사한다.

## 아직 완료하지 않은 것

새 캐릭터의 눈 깜박임·표정·주유 인사 프레임은 제작하지 않았다. 기존 72프레임 원본/좌표/웹 애니메이션 실험실은 보존했지만 현재 Android 제품의 새 캐릭터는 정지 이미지다. 프레임 제작 후 별도 시각 검토와 주행 중단/중복/모션 감소 검증을 거쳐야 한다. 정지 이미지에 회전이나 통째로 이동 효과를 붙여 새 애니메이션이라고 부르지 않는다.

실제 PLEOS/Devbox/실차 실행, 차량 데이터 연동과 OS HUN 외형은 이번에 확인하지 않았다. 배경은 장식용 이미지이며 실제 위치/경로 지도나 방문 중인 주유소 사진이 아니다. 추천과 거리에는 호스트 실제 데이터만 사용한다.

## 검증

- JDK/SDK doctor와 `python scripts/dev.py verify`: 성공. 단위 테스트 XML 결과는 155개, 실패·오류·건너뜀 0개다. 일부 Gradle 작업은 UP-TO-DATE 캐시를 사용했다.
- 마지막 보조 버튼 색상/하단 문구 수정 후 `:app:testDebugUnitTest :app:assembleDebug :app:assembleDebugAndroidTest :app:lintDebug`: 성공. 바뀐 앱 관련 항목만 다시 확인했다.
- 일반 Android API 35 에뮬레이터: SplitWindow 5개, VehicleScale 5개, NativeDemo 4개, SettingsMotion 1개, 총 15개 통과. 마지막 색상 수정 후 해당 정차 홈 테스트 1개 재실행 통과(새 테스트로 중복 합산하지 않는다). 건너뜀 0개. 기존 나머지 기기 테스트 7개는 미실행이다.
- Node 22 문법 검사와 패키지 검사 통과. 기존 웹 상태/아틀라스 테스트 18개 통과(건너뜀 0개). 기존 아틀라스 테스트를 새 캐릭터 애니메이션 검증이라고 해석하지 않는다.
- ego-browser로 1600px/1440px/420px 실제 렌더를 확인했다. 수평 넘침 없음, 76px 행동 버튼, Unknown 행동 없음, 내비 응답 대기/실패 후 재시도, 미루기 후 표면 전환 시 재노출 없음, 15초 알림 만료를 확인했다. 검토 작업 공간은 종료했다.
- 웹과 Android 홈/작은 창/낮은 창/대기 캡처를 직접 열어 봤다. Android 홈 캡처는 테스트가 공급한 예시 상태로 실제 Compose `AppShell`을 렌더한 화면이다. 실제 차량/PLEOS 실행 캡처는 아니다.
- PNG 투명 알파와 웹/Android 두 자산의 바이트 일치를 확인했다. `git diff --check` 통과. 커밋·푸시는 하지 않았다.

화면: [웹 홈](screenshots/sunset-home-20261112.png), [웹 작은 창](screenshots/sunset-compact-20261112.png), [웹 대기](screenshots/sunset-waiting-20261112.png), [Android 홈](screenshots/sunset-native-home-overview.png), [Android 좁은 창](screenshots/sunset-native-split-420x620-Moving.png), [Android 낮은 창](screenshots/sunset-native-split-640x340-Moving.png), [Android 대기](screenshots/sunset-native-split-420x620-Unknown.png).

자동 테스트 통과와 사용자의 화면 디자인 확인은 별개다. 실제 크기로 사람이 직접 보고 확인해야 한다.
