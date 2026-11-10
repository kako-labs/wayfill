# 뽀동이 원본 자료

출처: 사용자가 제공한 FUEL PILOT 디자인 아티팩트
https://claude.ai/artifact/F1hvn6xzMmDncVC8rWj8gY#page-003e6cf4d7ba

아티팩트 메뉴 → 내보내기 → HTML로 직접 내려받아 내장 manifest의 원본 이미지 bytes를 추출했다. 캐릭터 이미지를 다시 생성하거나 배경을 편집하지 않았다.

| 앱 파일 | 원본 내장 asset |
| --- | --- |
| podong-peek.png | 139bff80-ce23-412f-a2ec-f5494dc84d69.png |
| podong-wave.png | 이전 동일 아티팩트의 podong-wave.png (482×362) |
| podong-cheek.png | c2fad148-143f-4c25-a4b2-059f09295274.png |
| podong-wiggle.png | 124f9275-dd1d-4b12-9174-875d950c6710.png |
| podong-tuck.png | 1d2ae2c1-746d-4e3e-823d-d5baab290115.png |

원본의 0.7초 프레임 설명은 “볼 부비부비”지만 실제 원본 이미지는 측후면 포즈다. 현재 전달본은 그 이미지를 그대로 사용하며, 새 리깅/벡터 애니메이션을 제작했다고 주장하지 않는다. 빼꼼/퇴장은 카드 경계 클리핑과 위치 전환으로 보조한다. 반짝임은 코드로 표시한다.

`reference/claude-export.html`은 원본 GIF와 정지 포즈, 시간표 및 폰트 라이선스를 보존한 전체 아티팩트 내보내기다. 레퍼런스 GIF는 9프레임 반복 파일이며 실제 앱에서는 반복 GIF 대신 이벤트 기반 포즈 시퀀스를 한 번 재생한다.

웹/Compose는 오프라인 시스템 글꼴을 기본 사용한다. 원본 문서의 Google Fonts는 내보내기 내부에 원래 라이선스와 함께 보존되어 있으며 앱 UI의 필수 네트워크 의존성이 아니다.
