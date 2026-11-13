"""The screens of the WAYFILL screen review page, in the order the page shows them.

Story shots follow the demo video (tools/rec/wayfill_take.py, demo_take.py); `video` is the time in the captioned cut
(WayfillStory). Gallery shots come from tools/rec/capture_states.py. `look` is something a reviewer should check.
"""

CHAPTERS = [
    {"id": "depart", "title": "출발 전", "lead": "정차한 채 내비가 수원역(23km)을 안내하고 있습니다. WAYFILL을 열면 주행 가능 거리와 경로를 읽고 지금 주유가 필요한지 판단합니다."},
    {"id": "drive", "title": "주행", "lead": "주행할 때는 내비를 앞에 둡니다. WAYFILL은 뒤에서 계속 판단하다가, 여유 50km를 지킬 수 없게 되기 전에 내비 위 알림 카드와 음성으로 알려 줍니다."},
    {"id": "route", "title": "경로 반영", "lead": "바로 안내를 누르면 그 주유소를 Pleos 내비의 경유지로 넣어 달라고 요청하고, 내비가 실제로 받아들였는지 경유지 목록으로 확인합니다."},
    {"id": "after", "title": "주유 후", "lead": "주유 확인은 차량의 연료량 신호가 필요합니다. 전기차 에뮬레이터에는 이 신호가 없어 시연 모드의 예시 데이터로 보여 줍니다."},
]

STORY = [
    {"chapter": "depart", "file": "t01-nav-parked", "title": "내비 안내 중, 정차", "video": 6,
     "state": "판교에서 수원역(23km)으로 내비가 안내 중입니다. 주행 가능 80km, 기어 P.",
     "can": "하단 바의 WAYFILL 아이콘을 눌러 앱을 엽니다."},
    {"chapter": "depart", "file": "t02-app-loading", "title": "앱을 연 직후", "video": None,
     "state": "차량 연료와 내비 경로를 읽는 약 3초입니다. 영상에서는 이 구간을 건너뜁니다.",
     "can": "기다리면 판단이 나옵니다.",
     "look": "읽는 동안 '차량과 경로 정보를 확인할 수 없어요'와 '정보 확인 필요'가 보여 오류처럼 읽힙니다. '확인하고 있어요' 같은 진행 문구가 맞습니다."},
    {"chapter": "depart", "file": "t03-home-noaction", "title": "주유가 필요 없을 때", "video": 15,
     "state": "주행 가능 80km로 23km를 가도 57km가 남아 판단 여유 50km를 지킵니다. '이번 경로는 중간 주유 없이 갈 수 있어요.'",
     "can": "모니터링 시작, 주유 안내·기록·설정 탭으로 이동."},
    {"chapter": "depart", "file": "t04-monitoring", "title": "모니터링 켬", "video": 25,
     "state": "'모니터링 중'. 화면을 닫아도 백그라운드 서비스가 주행 가능 거리와 경로를 계속 확인합니다.",
     "can": "모니터링 중지, 차량 정보 다시 확인."},
    {"chapter": "drive", "file": "t05-nav-driving", "title": "주행 중에는 내비가 앞", "video": 37,
     "state": "기어 D, 60km/h(Virtual Console 입력). WAYFILL은 화면 뒤에서 판단합니다.",
     "can": "운전자는 아무것도 하지 않습니다."},
    {"chapter": "drive", "file": "t06-hun", "title": "알림 카드(HUN)와 음성", "video": 57,
     "state": "주행 가능을 콘솔로 80km에서 70km까지 낮췄습니다. 목적지에서 50km를 남길 수 없게 되자 내비 위에 '현재 경로의 주유 추천 · 17.7km 앞 · 경로 +4분 · 미래주유소' 카드가 뜨고 차량 음성이 안내합니다.",
     "can": "바로 안내 또는 나중에. 카드는 15초 뒤 사라집니다."},
    {"chapter": "route", "file": "t07-reroute", "title": "내비가 경로를 다시 찾음", "video": 71,
     "state": "바로 안내 직후. WAYFILL이 미래주유소를 경유지로 넣어 달라고 요청했고, 내비가 '경유지 1개 포함' 경로 후보를 보여 줍니다.",
     "can": "내비가 곧 안내를 시작합니다. 운전자가 다시 고를 수도 있습니다.",
     "changed": "경유지 요청을 한 번만 보내고 결과를 12초까지 기다리도록 바꿨습니다."},
    {"chapter": "route", "file": "t08-nav-waypoint", "title": "주유소를 거쳐 가는 안내", "video": 80,
     "state": "내비가 미래주유소(18km, 21분)를 먼저 안내합니다. 최종 목적지는 그대로 수원역입니다.",
     "can": "그대로 주행합니다."},
    {"chapter": "route", "file": "t09-moving-success", "title": "주행 중 WAYFILL을 다시 열면", "video": 89,
     "state": "'주유소 경로가 준비됐어요 · 차량 내비게이션의 경로를 따라가세요.' 내비가 경유지 추가를 마쳤거나 내비 경유지 목록에 그 주유소가 있을 때만 이 문구를 씁니다.",
     "can": "내비로 돌아갑니다."},
    {"chapter": "route", "file": "t10-home-on-route", "title": "정차 후 홈", "video": 96,
     "state": "'미래주유소에 들렀다 가는 경로로 안내 중이에요.' 같은 주유소를 다시 권하지 않습니다. 목적지 약 24km는 경유지를 포함한 거리입니다.",
     "can": "모니터링 중지, 주유 안내 탭에서 상세 확인.",
     "changed": "내비 경유지를 읽어 '경유지 안내 중' 상태를 새로 표시합니다."},
    {"chapter": "after", "file": "d-refuel-full", "title": "주유 확인", "video": 106, "demo": True,
     "state": "주유량이 늘어난 것을 확인하면 뽀동이가 한 번 반기고 새 연료 상태로 다시 판단합니다. 예시: 40L, 확인 후 주행 가능 550km.",
     "can": "확인했어요."},
    {"chapter": "after", "file": "d-records", "title": "주유 기록", "video": 114, "demo": True,
     "state": "확인된 주유가 이 기기에 최근 90일, 최대 100건까지 남습니다. 갤러리용으로 세 번 주유해 3건입니다.",
     "can": "기록 삭제.",
     "look": "5L 기록도 '확인 후 주행 가능 550km'로 40L와 같습니다. 시연 모드 예시 데이터의 고정값입니다."},
]

REAL = [
    {"file": "c04-home-recommend", "title": "정차 중 주유 추천", "state": "주행 가능 70km, 목적지 23km. '가는 길에 주유하고 가요 · 미래주유소 17.7km 앞 · 경로 +4분'.",
     "note": "모니터링이 꺼져 있는 것은 촬영 도구가 목적지를 바꾸며 앱을 다시 띄웠기 때문입니다."},
    {"file": "c05-analysis-recommend", "title": "주유 안내 탭 · 추천", "state": "추천 주유소와 이유, 설정한 여유거리. '추천 근거 자세히 보기'로 계산을 펼칩니다."},
    {"file": "c01-analysis-on-route", "title": "주유 안내 탭 · 경유지 안내 중", "state": "경유지를 넣은 뒤의 상태. 경로 요약과 모니터링 상태를 함께 보여 줍니다."},
    {"file": "c06-driving-recommend", "title": "주행 중 WAYFILL을 앞에 둘 때", "state": "큰 숫자 하나(17.7km 앞)와 주유소 이름, 바로 안내·나중에 버튼만 남깁니다."},
    {"file": "c07-driving-noaction", "title": "주행 중 · 주유가 필요 없을 때", "state": "'필요한 순간에 알려드릴게요.'",
     "look": "보조 문구 '신호가 확인되면 현재 경로에 맞춰 안내할게요'는 판단이 끝난 상태인데 신호를 기다리는 것처럼 읽힙니다."},
    {"file": "c08-home-nearby", "title": "목적지가 없을 때", "state": "내비 안내를 끝내면 현재 위치에서 가까운 주유소를 고릅니다. 주행 가능 60km, (주)새서울석유 세종 1.7km.",
     "look": "목적지가 없는데 '가는 길에 주유하고 가요', '현재 경로의 주유 추천', '이번 경로의 추천', '목적지까지 확인 중'이 보입니다. 주변 안내용 문구가 필요합니다."},
    {"file": "c09-home-energy-missing", "title": "주행 가능 거리를 읽을 수 없을 때", "state": "차량 에너지 권한이 없으면 '예시 수치로 대신하지 않습니다'라고 밝히고 판단하지 않습니다."},
    {"file": "c10-home-fuel-unknown", "title": "연료 종류를 모를 때", "state": "차량이 연료 종류를 알려 주지 않으면 설정에서 고르라고 안내합니다. 앱을 처음 설치한 상태입니다."},
    {"file": "c11-settings-fuel-unset", "title": "설정 · 연료 미선택", "state": "휘발유·고급휘발유·경유·LPG 중 아직 고르지 않았어요."},
    {"file": "c12-settings-fuel-set", "title": "설정 · 휘발유 선택", "state": "고른 연료로 주유소를 찾습니다. 차량이 연료 종류를 알려 주면 그 값을 먼저 씁니다."},
    {"file": "c13-home-after-fuel", "title": "연료를 고른 뒤의 홈", "state": "주행 가능 60km, 목적지 23km. '앞 주유소에서 주유하고 가요 · 주유소티파니블루 4.3km 앞 · 경로 +5분'."},
    {"file": "c03-settings", "title": "설정 전체", "state": "판단 여유거리(20~100km, 10km 단위), 연료 종류, 정차 중 캐릭터 움직임."},
    {"file": "c02-records-empty", "title": "기록 · 비어 있음", "state": "실제 앱에는 아직 확인된 주유가 없습니다."},
]

DEMO_GROUPS = [
    {"title": "정차", "shots": [
        {"file": "d-parked-recommendation", "title": "목적지 전에 주유 필요", "state": "주행 가능 150km, 목적지 250km. 시연 주유소 A 15km 앞."},
        {"file": "d-parked-passing", "title": "앞 주유소를 지나치면 부족", "state": "3km 앞 주유소를 지나치면 다음 주유소까지 연료가 부족합니다."},
        {"file": "d-parked-noaction", "title": "중간 주유 없이 도착", "state": "주행 가능 400km, 목적지 250km."},
        {"file": "d-parked-nearby", "title": "목적지 없음", "state": "도달 가능한 주유소 중 가장 가까운 곳.", "look": "실제 앱의 '목적지가 없을 때'와 같은 문구 문제."},
        {"file": "d-parked-stale", "title": "연료 정보가 오래됨", "state": "'최신 정보 확인 중'. 새 값을 받을 때까지 판단을 미룹니다."},
        {"file": "d-parked-unsupported", "title": "SDK·권한 없음", "state": "'연결 필요'. 지원 SDK나 권한이 준비되면 다시 확인합니다."},
        {"file": "d-parked-unavailable", "title": "연료 정보 없음", "state": "'정보 확인 필요'. 차량이 값을 주지 않을 때."},
    ]},
    {"title": "주행", "shots": [
        {"file": "d-moving-recommendation", "title": "목적지 전에 한 번 주유", "state": "15km 앞 · 경로 +2분."},
        {"file": "d-moving-passing", "title": "지나치기 전에 확인", "state": "3km 앞. 지나치면 다음 주유소까지 연료 부족 예상."},
        {"file": "d-moving-noaction", "title": "주유 필요 없음", "state": "'필요한 순간에 알려드릴게요.'", "look": "실제 앱과 같은 보조 문구 문제."},
    ]},
    {"title": "내비 요청 결과", "shots": [
        {"file": "d-nav-success", "title": "경유지 추가 성공", "state": "'주유소 경로가 준비됐어요.'"},
        {"file": "d-nav-failure", "title": "경유지 추가 실패", "state": "'경유지를 추가하지 못했어요. 다시 시도해 주세요.' 다시 안내·홈으로."},
    ]},
    {"title": "주유 확인", "shots": [
        {"file": "d-refuel-small", "title": "조금 주유", "state": "'조금 더 가볍게 출발해요' · 5L.", "look": "5L인데 확인 후 주행 가능이 550km입니다(예시 데이터 고정값)."},
        {"file": "d-refuel-full", "title": "가득 주유", "state": "'든든하게 채웠어요' · 40L · 550km."},
        {"file": "d-refuel-recovery", "title": "연료가 낮을 때 주유", "state": "'다시 달릴 힘을 채웠어요' · 40L."},
        {"file": "d-records", "title": "기록", "state": "위 세 번의 주유가 기록에 남았습니다."},
    ]},
]

# Chapter starts in each cut (seconds), from src/story/scenes.ts plus the title card.
VIDEOS = [
    {"id": "captions", "label": "자막판", "file": "video/wayfill-story-720p.mp4", "length": "2:11",
     "note": "배경음악 없이 차량 음성과 내비 음성만 들립니다.", "chapters": [3.4, 26.8, 61.3, 98.6, 120.6]},
    {"id": "narration", "label": "내레이션판", "file": "video/wayfill-story-tts-720p.mp4", "length": "2:15",
     "note": "자막과 같은 문장을 AI 음성(ElevenLabs)으로 읽습니다. whisper로 18문장 모두 끝까지 들리는 것을 확인했습니다.", "chapters": [5.8, 29.2, 63.7, 101.0, 123.0]},
]

LOOKS = [
    ("t02-app-loading", "앱을 연 직후 약 3초", "읽는 중인데 '차량과 경로 정보를 확인할 수 없어요'가 보입니다."),
    ("c08-home-nearby", "목적지가 없을 때의 홈", "'가는 길에', '현재 경로', '이번 경로', '목적지까지 확인 중'이 주변 안내와 맞지 않습니다."),
    ("c07-driving-noaction", "주행 중 주유가 필요 없을 때", "보조 문구가 신호를 기다리는 상태처럼 읽힙니다."),
    ("d-refuel-small", "시연 모드 소량 주유", "5L 주유 뒤에도 주행 가능 550km로 표시됩니다."),
]

DISCLOSURES = [
    "실제 앱을 Pleos Connect 에뮬레이터(2560×1440)에서 녹화했습니다. 화면 속도는 모두 1배속입니다.",
    "기어·속도·주행 가능 거리는 에뮬레이터의 Virtual Console로 입력했고, 영상에 입력 라벨을 표시했습니다.",
    "에뮬레이터 내비는 위치가 움직이지 않아, 주행하며 줄어드는 주행 가능 거리를 콘솔로 넣었습니다.",
    "주유 확인과 기록은 차량 연료량 신호가 필요해 시연 모드의 예시 데이터로 보여 줍니다. 시연 모드 화면 위쪽의 개발용 버튼 줄은 라벨로 가렸습니다.",
    "주유소 위치·가격은 오피넷, 경로와 우회 시간은 카카오모빌리티 길찾기에서 실제로 받아 온 값입니다.",
]
