# FUEL PILOT

연료가 부족해진 뒤가 아니라, 현재 경로에서 주유하기 좋은 마지막 안전 기회를 놓치기 전에 알려 주는 Pleos 차량용 주유 보조 앱입니다.

현재 제품 기준선은 MVP 단계입니다. 실제 Pleos Vehicle SDK, NaviHelper, 주유소 POI 공급자 연결은 차량·SDK 환경에서 별도로 검증해야 하며, 실제 연동이 없을 때 운영 빌드가 합성 데이터로 대체해서는 안 됩니다.

## 제품 개발 이어서 하기

1. `git clone https://github.com/kako-labs/fuel-pilot.git`
2. 통합 브랜치 `integrate/podong-approved`로 전환합니다.
3. [최신 이어가기 안내](CONTINUE-HERE.md)와 [제품 인계 문서](docs/handoff.md)를 먼저 읽습니다.
4. `python scripts/dev.py doctor`로 JDK 17과 Android SDK를 확인합니다.
5. 핵심 엔진 테스트: `./gradlew :core:model:test --no-daemon --max-workers=1`

제품 설계와 구현 순서는 [`docs/superpowers/specs`](docs/superpowers/specs)와 [`docs/superpowers/plans`](docs/superpowers/plans)에 있습니다.

11월 11일 제품 준비 변경에는 홈·정차 설정, 공유 런타임/모니터링 서비스, 목적지/경유지 요청 구분, debug 전용 네이티브 시연, 주유소 서버 경계와 Android 화면 검사/CI가 포함됩니다. [검증과 실제 화면](docs/product-readiness-2026-11-11.md), [다른 기기용 소스 전달](docs/submission/portable-source.md), [제출 문서](docs/submission/README.md)를 확인하세요. SDK가 없는 기본 앱은 미지원 상태를 표시하며, debug 앱 홈에서만 ‘시연 모드 · 예시 데이터’에 들어갑니다. release에는 시연 코드가 없습니다.

## 승인된 뽀동이 UI 기준선

주유가 필요한 이유를 먼저 설명하고, 메시지마다 뽀동이가 함께하며 정차·주유 후에는 귀엽게 반응하는 승인 UI 전달본을 함께 보존합니다.

- [승인 버전 시작 안내](START-HERE.md)
- [UI 세션 인계문](docs/UI-SESSION-HANDOFF.md)
- [앱 개발 세션 인계문](docs/APP-SESSION-HANDOFF.md)
- [사용자 승인 기준과 파일 매핑](docs/APPROVED-BASELINE.md)
- [Android 연결 계약](docs/ANDROID-INTEGRATION.md)

고정 태그와 [승인 버전 다운로드](https://github.com/kako-labs/fuel-pilot/releases/tag/podong-ui-approved-2026-11-10)는 `podong-ui-approved-2026-11-10`입니다. 로컬 서버 없이 다음 HTML 파일을 브라우저에서 열 수 있습니다.

- `web/animation-lab.html`: 5개 동작·72프레임 애니메이션 비교
- `web/index.html`: 설치 없이 실행하는 오프라인 인터랙티브 데모 11개 상황
- `feature/assist`: 상태 reducer와 원본 캐릭터 리소스를 포함한 통합 Compose 라이브러리
- `feature/assist/build/outputs/aar/assist-debug.aar`: 검증 빌드에서 생성되는 Android UI 라이브러리
- `reference/claude-export.html`: 사용자가 지정한 뽀동이 원본 모션과 디자인 자료

UI 기준선 검증: `npm.cmd test`, `npm.cmd run verify`, `.\\gradlew.bat :feature:assist:testDebugUnitTest :feature:assist:assembleDebug --no-daemon --max-workers=1 --console=plain`.

## 현재 앱 실행

승인 UI를 앱 시작 화면에 연결했습니다. SDK 없는 환경에서는 뽀동이와 명시적인 미지원 안내를 표시합니다. 실제 포트 연결 지점은 AppDependencies이며 화면에 시연 데이터를 주입하지 않습니다. [최신 검증 기록](docs/verification-2026-11-11.md)을 확인하세요.

```powershell
.\gradlew.bat :app:assembleDebug --no-daemon --max-workers=1
```

설치할 APK: `app/build/outputs/apk/debug/app-debug.apk`. 실제 Pleos 차량 연동 검증은 별도입니다.
