# 승인한 소프트 선셋 · 2026-11-12

사용자가 `home-reference.png`, `podong-reference.png`를 첨부하고 “이걸로 가자”라고 선택했다. 앞선 3종 컨셉이나 기존 네이비 홈보다 이 두 이미지가 현재 아트디렉션의 우선 기준이다.

- 원본 홈의 글자/버튼을 배경에 구워 넣지 않는다. 실제 텍스트와 행동을 코드로 구성한다.
- 따뜻한 석양, 주유소 캐노피, 회갈색 반투명 패널, 크림색 텍스트, 금빛 주요 버튼을 사용한다.
- 새 캐릭터는 둥근 노란 몸과 몸을 감싼 도로 띠를 유지한다.
- 정차 홈 외의 주행/연결 대기/설정에도 같은 캐릭터를 사용한다. 주행 중에는 정지 이미지다.

## 생성 자산

내장 `image_gen` 도구로 원본을 편집했다. CLI/API fallback은 사용하지 않았다. 원본은 보존했다.

1. `web/assets/sunset/podong-sunset.png`: 승인 캐릭터를 투명 배경으로 분리. 웹/Android 동일 PNG다.
2. `web/assets/sunset/sunset-forecourt.png`: 승인 홈에서 UI와 캐릭터를 제거한 석양 배경. 웹/Android 동일 PNG다.

### 캐릭터 편집 프롬프트

Precise background extraction of the provided approved Podong character. Keep the EXACT same golden round soft 3D creature, same silhouette, same eye shape, same smiling mouth, same cheek blush, same short limbs, same raised waving arm and same glowing road ribbon spiralling diagonally around its body with the same dashed lane markings. Do not redesign, restyle, change proportions or add details. Remove the gray studio background and floor shadow only. Output a high-resolution clean isolated character on genuinely transparent alpha background. Preserve warm rim lighting and translucent road ribbon glow, no new ornaments or sparkles, no text. Whole character visible with a modest consistent transparent margin. This will be a production UI PNG asset, not a mockup.

### 배경 편집 프롬프트

Edit target: attached approved Fuel Pilot sunset UI concept. Extract and reconstruct ONLY its scenic background as a standalone wide landscape production background asset. Remove ALL lettering, branding, numbers, icons, UI panels, buttons, borders, character, road ribbon, labels, all overlays. Keep the exact warm soft sunset art direction: mauve peach sky with delicate clouds, distant low mountains, small golden sun low near center-right horizon, a calm road/rest stop forecourt, guardrail sweeping from left, a modern warm illuminated fuel station canopy entering from the far RIGHT edge with a small pump below, no readable signs. Top half predominantly open sky, left two thirds uncluttered and muted for overlay typography. Foreground bottom half softly shaded espresso/taupe asphalt, subtly reflective but not shiny wet. Preserve composition and cinematic illustration realism, warm golden hour atmosphere, colors matching original. No character or creature anywhere, no text or UI elements. Landscape 16:10, high-quality background image, edge to edge.
