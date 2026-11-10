# FUEL PILOT

연료가 부족해진 뒤가 아니라, 현재 경로에서 주유하기 좋은 마지막 안전 기회를 놓치기 전에 알려 주는 Pleos 차량용 주유 보조 앱입니다.

현재 구현은 합성 데이터를 이용하는 MVP 단계입니다. 실제 Pleos Vehicle SDK, NaviHelper, 주유소 POI 공급자 연결은 차량·SDK 환경에서 별도로 검증해야 합니다.

## 이어서 작업하기

1. `git clone https://github.com/kako-labs/fuel-pilot.git`
2. `git switch song0328/last-safe-fuel-mvp`
3. [인계 문서](docs/handoff.md)를 먼저 읽습니다.
4. `python scripts/dev.py doctor`로 JDK 17과 Android SDK를 확인합니다.
5. 핵심 엔진 테스트: `./gradlew :core:model:test --no-daemon --max-workers=1`

제품 설계와 구현 순서는 `docs/superpowers/specs`와 `docs/superpowers/plans`에 있습니다.
