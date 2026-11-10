# UI 세션 인계

## 다음 기기에서 그대로 전달할 문장

```text
https://github.com/kako-labs/fuel-pilot 의 podong-ui-approved-2026-11-10 태그를 기준으로 UI 작업을 이어줘. START-HERE.md, docs/APPROVED-BASELINE.md, docs/UI-SESSION-HANDOFF.md를 먼저 읽어줘. 사용자가 승인한 것은 16프레임의 큰 정차 동작 4종 + 차분한 8프레임, 총 72프레임 버전이야. web/index.html이 전체 UI 기준이고 web/animation-lab.html이 모션 비교 화면이야. 메시지마다 뽀동이를 유지하고, 주행 중에는 작고 차분하게, 정차·주유 후에는 큰 동작을 보여줘. 이 기준을 보존하면서 내가 요청하는 UI 수정만 진행하고, Android 모듈과 달라지는 사항을 docs/SESSION-PROGRESS.md에 기록해줘. 과거 8프레임 버전이나 원본 아티팩트의 오래된 UI로 되돌리지 마.
```

## 확인할 화면

ZIP을 풀고 `web/animation-lab.html`과 `web/index.html`을 Chrome/Edge에서 연다. 별도 설치나 이 PC의 localhost 주소는 필요하지 않다. 모바일 기기에서 GitHub의 HTML 코드 뷰는 실제 앱 미리보기가 아니므로, 노트북 브라우저에서 내려받은 파일을 연다.

- 추천 이유, 주유소, 두 버튼의 순서와 문구.
- 11개 시연 상황에서 메시지 곁에 뽀동이가 표시되는지.
- 큰 정차 동작의 준비/최대 표현/회복과 주행 아바타의 차분함.
- 모션 종료 후에도 캐릭터가 남는지, 출발 시 즉시 작아지는지.
- 상세/설정의 머리줄 아바타와 좁은 화면의 문구/버튼 배치.

색은 honey gold `#E3A33C`, graphite `#171512`, coral `#FF9A88` 기준이다. 샘플의 연료·거리·주유량·시간은 시연 데이터다. 차량 앱에 사용할 수치는 호스트 데이터로 공급한다.

웹 비교 화면의 반복 미리보기는 관찰 편의를 위한 기능이다. 이 반복을 실제 주유 완료 이벤트에 적용하지 않는다. 사용자 후속 요청으로 디자인을 바꾸면 변경 이유, 적용 파일, 검증 결과와 코드 세션에 필요한 변경을 `docs/SESSION-PROGRESS.md`에 기록한다.
