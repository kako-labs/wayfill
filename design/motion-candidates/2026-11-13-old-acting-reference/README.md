# 뽀동이 모션 V2 제작 원본과 승인 32포즈

현재 소프트 선셋·도로 띠 뽀동이의 외형은 유지하고, 과거 72프레임 버전의 전신 연기 구조를 참고해 만든 모션 원본이다. 2026-11-13 후속 검수에서 각 동작을 32개의 실제 포즈로 확장해 제품 자산에 연결했다.

- `recommend`: 준비 자세, 큰 방향 제시, 잠깐 유지, 부드러운 복귀.
- `small`: 추천과 분리한 짧은 통통 반응.
- `full`: 웅크림에서 양팔 V자로 이어지는 큰 축하.
- `recovery`: 지친 자세에서 기지개와 안도 표정으로 회복.
- `calm`: 기존 제품의 6초 눈 깜박임을 그대로 사용한다.

초기 후보는 1256×1256의 4×4 시트다. `direct-32/raw/`는 built-in image generation으로 그린 8×4 / 32포즈 원본, `direct-32/clean/`은 셀 중앙 캐릭터와 연결되지 않은 배경 잔여 픽셀을 제거한 제품 원본이다. 검수한 clean 시트가 `web/assets/sunset/`과 Android `drawable-nodpi/sunset_*.png`에 같은 바이트로 설치된다.

`smooth-32/`는 ffmpeg optical-flow/크로스페이드 비교 결과다. 얼굴·손의 순간 왜곡 또는 이중상이 확인돼 제품에 사용하지 않는다. 제품 설치는 `tools/install-motion-sheets.ps1`, 알파 정리는 `tools/clean-sprite-alpha.ps1`로 재현한다.

생성 방식: built-in image generation. 현재 소프트 선셋 시트를 외형 참조로, 과거 `web/assets/animation/podong-atlas.png`를 동작 연기 참조로 사용했다. 직접 그린 32포즈는 겹눈·잔상·optical-flow 왜곡이 없는지 네 시트를 육안 검수한 뒤 채택했다.
