# 승인 기준 · 2026-11-10

> 2026-11-13 최신 시각 디자인: 사용자가 석양 홈과 도로 띠 뽀동이를 선택한 뒤, 과거 버전의 큰 전신 연기를 참고해 추천·소량·충분한 주유·저연료 회복을 각각 32개의 실제 포즈로 보완했다. 웹과 Android는 같은 8×4 시트와 시간표를 사용한다. 주행/Unknown/HUN은 정지 또는 캐릭터 미표시, 정차 추천과 검증된 주유 이벤트만 1회 표현 모션이다. 아래 72프레임은 과거 승인 자산이며 역사·호환 참고용이다. 현재 범위와 검증은 PRE-PLEOS-READINESS-2026-11-13.md를 따른다. 이벤트 중복 억제·주행 취소·모션 감소 계약은 유지한다.

사용자가 표현을 키운 수정본을 보고 “좋아 이걸로 잘 깃허브에 기록해줘”라고 승인했다. 이 문서는 다른 기기의 UI 세션과 앱 개발 세션이 같은 결과물을 기준으로 삼기 위한 기록이다.

| 항목 | 확정 기준 |
| --- | --- |
| 저장소 | `kako-labs/fuel-pilot` (비공개, 해당 계정/조직 접근 권한 필요) |
| 작업 브랜치 | `ui/podong-delivery-20261110` |
| 승인된 UI/코드 커밋 | `95d08776d71b284b929f7cd4d3416ef355406efe` |
| 고정 인계 태그 | `podong-ui-approved-2026-11-10` — 승인 코드와 이번 인계 문서를 함께 고정 |
| 다운로드 | 동일 태그의 GitHub Release: UI·소스 ZIP, debug AAR, SHA256SUMS.txt |
| UI 세션 | [UI-SESSION-HANDOFF.md](UI-SESSION-HANDOFF.md) |
| 코드 세션 | [APP-SESSION-HANDOFF.md](APP-SESSION-HANDOFF.md) |

## 유지할 UX

주유가 필요한 이유 → 추천 주유소 → 행동 순서다. 버튼은 “여기서 주유하기 / 계속 가기”. 메시지에는 항상 뽀동이를 표시한다. 주행 또는 상태 미확인에서는 작은 차분한 모습, 정차·주유 후에는 큰 표정과 팔 동작을 보여준다. 인사 종료 후에도 캐릭터는 남는다.

활발한 동작 4종(recommend/small/full/recovery)은 각각 16프레임, calm은 8프레임으로 총 72프레임이다. 준비 동작 → 큰 반응 → 회복 흐름을 따른다. 가득 주유는 웅크렸다 양팔 펼치기, 바닥에서 회복은 지친 상태에서 큰 기지개다. 이전 버전의 빠른 앞/뒤 전환은 현재 동작으로 사용하지 않는다.

일반 주유 2초, 소량 1.6초, 가득/회복 2.4초다. 실제 동작 이후 마지막 프레임 유지 시간이 포함될 수 있다. 동일 이벤트 ID는 한 번만 처리한다. 출발 시 큰 동작을 즉시 중단하고 재정차해도 과거 인사를 다시 재생하지 않는다. 모션 설정 OFF/접근성 모션 감소 시 차분한 정지 모습은 유지한다. 소리는 없다.

## 파일을 함께 가져오기

| 용도 | 현재 파일 |
| --- | --- |
| 전체 UI 기준 | `web/index.html`, `web/style.css`, `web/app.js`, `web/state.js` |
| 모션 비교 | `web/animation-lab.html` (반복은 미리보기 전용) |
| 웹 프레임/재생기 | `web/animation-data.js`, `web/podong-animation.js`, `web/assets/animation/` |
| 네이티브 모듈 | `feature/assist/`의 `dev.fuelpilot.ui` 소스·리소스·테스트 |
| 네이티브 모션 | `PodongAnimation.kt`, `PodongClips.kt`, `res/drawable-nodpi/podong_atlas.png` |
| 빌드된 라이브러리 | 검증 시 생성되는 `feature/assist/build/outputs/aar/assist-debug.aar` |

아틀라스·좌표·표시 영역·프레임 시간은 한 세트다. 예전 PNG 포즈 파일이나 `reference/claude-export.html`의 이전 문구/판정을 최신 기준으로 다시 적용하지 않는다. 폴더의 이전 자료는 참고·호환 보존용이다.

## 확인된 범위

Node 테스트 17개, Android JUnit 11개, Compose 라이브러리 debug AAR 빌드가 통과했다. 브라우저에서 활성 동작 각각의 0~15번 프레임, 주행 중단, 동일 이벤트 억제와 390px 폭을 확인했다. 세부 기록은 [VERIFICATION.md](VERIFICATION.md), 생성 한계는 `web/assets/animation/QA-NOTES.md`에 있다.

기존 회사 앱 소스와 실제 Pleos 기기는 이 전달본에서 연결·실행하지 않았다. 다음 코드 세션이 호스트 이벤트/지도/저장 계층을 연결하고 실제 기기 검증 결과를 별도로 기록해야 한다. 8~10fps의 스프라이트이며 리깅된 벡터 애니메이션은 아니다.
