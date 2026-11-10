# FUEL PILOT · 뽀동이 UI Kit

주유가 필요한 이유를 먼저 설명하고, 메시지마다 뽀동이가 함께하고 정차·주유 후에는 귀엽게 반응하는 UI 전달본입니다.

**내일 회사 PC에서는 [START-HERE.md](START-HERE.md)부터 읽으세요.**

- `web/animation-lab.html`: 새로 그린 5동작·40프레임 애니메이션 비교
- `web/index.html`: 설치 없이 더블클릭하는 오프라인 인터랙티브 데모, 11개 상황
- `android/fuel-pilot-ui`: 상태 reducer와 원본 캐릭터 리소스를 포함한 Compose 라이브러리
- `release-artifacts/fuel-pilot-ui-debug.aar`: 빌드된 Android UI 라이브러리
- `docs/ANDROID-INTEGRATION.md`: 기존 앱의 차량·내비·주유 완료 이벤트 연결 계약
- `reference/claude-export.html`: 사용자가 지정한 뽀동이 원본 모션과 디자인 자료

실제 지도/차량/주유소 데이터 연동은 기존 앱에서 연결합니다. 원격 main을 변경하지 않는 `ui/podong-delivery-20261110` 브랜치의 전달 작업입니다.

검증: `npm test`, `npm run verify`, `android/gradlew :fuel-pilot-ui:testDebugUnitTest :fuel-pilot-ui:assembleDebug`.
