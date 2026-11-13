/**
 * WAYFILL 2:30 edit plan.
 *
 * This is an editorial source of truth modeled after scenes (1).ts.
 * It does not claim that PLEOS/Gleo integration has been verified.
 */

export const FPS = 30;

export const CHAPTERS = [
  '출발 전',
  '주행',
  '주유 안내',
  '도착·주유',
  '다시 판단',
] as const;

type Chapter = (typeof CHAPTERS)[number];

type Caption = {
  text: string;
  from?: number;
  to?: number;
};

export type WayfillScene = {
  id: string;
  chapter: Chapter;
  from: number;
  dur: number;
  screen: string;
  action: string;
  caps: Caption[];
  audio?: string;
  skip?: string;
  dissolve?: boolean;
  verifiedOnly?: 'pleos' | 'gleo';
};

export const SCENES: WayfillScene[] = [
  {
    id: 'open',
    chapter: '출발 전',
    from: 0,
    dur: 5,
    screen: 'WAYFILL logo',
    action: '어두운 화면에서 로고가 나타난 뒤 차량 화면으로 디졸브',
    caps: [{ text: '주유 걱정 없이 가는 길에 가장 알맞은 주유소를 찾아줍니다' }],
    audio: '배경 음악 시작',
    dissolve: true,
  },
  {
    id: 'launch',
    chapter: '출발 전',
    from: 5,
    dur: 6,
    screen: 'PLEOS parked launcher',
    action: '하단 앱 바에서 WAYFILL 선택',
    caps: [{ text: '출발하기 전에 하단 바에서 WAYFILL을 엽니다' }],
    audio: '탭 효과음',
  },
  {
    id: 'parked-home',
    chapter: '출발 전',
    from: 11,
    dur: 8,
    screen: 'WAYFILL parked home',
    action: '목적지·남은 거리·주행 가능 거리·뽀동이를 전체 화면으로 제시',
    caps: [{ text: '첫 화면에는 목적지와 남은 연료가 함께 보입니다' }],
  },
  {
    id: 'range-gap',
    chapter: '출발 전',
    from: 19,
    dur: 8,
    screen: 'WAYFILL parked home',
    action: '목적지 95km와 주행 가능 75km를 차례로 확대',
    caps: [{ text: '목적지까지 95km인데 지금 갈 수 있는 거리는 75km입니다' }],
  },
  {
    id: 'station-card',
    chapter: '출발 전',
    from: 27,
    dur: 8,
    screen: 'recommended station card',
    action: '우회 거리·가격·영업 여부를 차례로 강조',
    caps: [{ text: '가는 길에서 크게 벗어나지 않는 주유소를 먼저 보여줍니다' }],
  },
  {
    id: 'monitor-on',
    chapter: '출발 전',
    from: 35,
    dur: 6,
    screen: 'monitoring control',
    action: '모니터링 시작을 누르고 활성 상태 확인',
    caps: [{ text: '안내를 받으려면 출발 전에 모니터링을 켭니다' }],
    audio: '탭·상태 전환음',
  },
  {
    id: 'drive-start',
    chapter: '주행',
    from: 41,
    dur: 8,
    screen: 'navigation plus WAYFILL small window',
    action: '기어 D 전환 후 사용자가 유지한 PLEOS 멀티 윈도우에서 내비와 WAYFILL을 함께 표시',
    caps: [{ text: '주행 중에는 WAYFILL을 내비 옆에 작게 띄워둘 수 있습니다' }],
  },
  {
    id: 'background-monitoring',
    chapter: '주행',
    from: 49,
    dur: 6,
    screen: 'vehicle navigation',
    action: '사용자가 시스템 윈도우 배치를 바꾸어 내비를 크게 표시하고 WAYFILL은 종료하지 않은 채 화면에서 밀려남',
    caps: [{ text: '내비만 보고 싶을 때는 WAYFILL을 화면 뒤로 보낼 수 있습니다' }],
  },
  {
    id: 'hun-offer',
    chapter: '주유 안내',
    from: 55,
    dur: 8,
    screen: 'navigation with WAYFILL HUN',
    action: '내비 위에 바로 안내·나중에 버튼이 있는 짧은 카드 표시',
    caps: [{ text: '주유할 때가 되면 내비 위에 짧은 안내 카드가 뜹니다' }],
    audio: '실제 HUN 안내 음성',
    verifiedOnly: 'pleos',
  },
  {
    id: 'hun-choice',
    chapter: '주유 안내',
    from: 63,
    dur: 10,
    screen: 'navigation with WAYFILL HUN',
    action: '두 선택지를 보여준 뒤 바로 안내 선택. Gleo 검증 시 후반부를 음성 선택으로 교체',
    caps: [{ text: '바로 안내와 나중에 중에서 하나를 고르면 됩니다' }],
    audio: '버튼 선택음',
    verifiedOnly: 'pleos',
  },
  {
    id: 'route-pending',
    chapter: '주유 안내',
    from: 73,
    dur: 7,
    screen: 'navigation request pending',
    action: '추천 주유소를 내비 경유지로 요청하고 결과를 기다림',
    caps: [{ text: '바로 안내를 누르면 주유소를 경유지로 요청합니다' }],
    verifiedOnly: 'pleos',
  },
  {
    id: 'route-confirmed',
    chapter: '주유 안내',
    from: 80,
    dur: 7,
    screen: 'navigation route confirmed',
    action: '실제 성공 응답 뒤에만 경로 반영 상태 표시',
    caps: [{ text: '내비에 경로가 바뀌면 주유소 안내가 시작됩니다' }],
    audio: '성공음 1회',
    verifiedOnly: 'pleos',
  },
  {
    id: 'split-window',
    chapter: '주행',
    from: 87,
    dur: 9,
    screen: 'navigation plus WAYFILL small window',
    action: '사용자가 PLEOS 멀티 윈도우에서 WAYFILL을 다시 열고 남은 거리와 차분한 뽀동이만 표시',
    caps: [{ text: '원하면 WAYFILL을 다시 열어 남은 거리만 볼 수 있습니다' }],
    verifiedOnly: 'pleos',
  },
  {
    id: 'arrive-park',
    chapter: '도착·주유',
    from: 96,
    dur: 7,
    screen: 'station arrival',
    action: '추천한 주유소에 도착한 상태를 표시',
    caps: [{ text: '추천한 주유소에 도착했습니다' }],
    skip: '주유소 도착',
  },
  {
    id: 'wait-for-refuel',
    chapter: '도착·주유',
    from: 103,
    dur: 6,
    screen: 'station waiting state',
    action: '주유 중에는 별도의 조작 없이 주유 완료를 기다림',
    caps: [{ text: '주유하는 동안에는 화면을 건드릴 필요가 없습니다' }],
  },
  {
    id: 'refuel-reaction',
    chapter: '도착·주유',
    from: 109,
    dur: 11,
    screen: 'refuel complete with Podong',
    action: '주유 완료 이벤트에 한 번 반응한 뒤 동일 이벤트 재입력으로 중복 억제 확인',
    caps: [
      { text: '주유가 끝나면 뽀동이가 바로 반겨줍니다', from: 0, to: 6 },
      { text: '한 번 주유할 때 뽀동이도 한 번만 반응합니다', from: 6, to: 11 },
    ],
    audio: '주유 완료음과 뽀동이 효과음 1회',
    skip: '주유 후',
  },
  {
    id: 'recalculate',
    chapter: '다시 판단',
    from: 120,
    dur: 7,
    screen: 'updated range decision',
    action: '새 연료량과 늘어난 주행 가능 거리로 경로 재판단',
    caps: [{ text: '새로 채운 연료에 맞춰 남은 길을 다시 확인합니다' }],
  },
  {
    id: 'history',
    chapter: '다시 판단',
    from: 127,
    dur: 7,
    screen: 'refuel history',
    action: '방금 확인된 기록을 열고 예시 데이터 표기를 유지',
    caps: [{ text: '방금 주유한 내용은 기록에서 다시 볼 수 있습니다' }],
  },
  {
    id: 'unknown-safe',
    chapter: '다시 판단',
    from: 134,
    dur: 8,
    screen: 'vehicle data unknown',
    action: 'Unknown 또는 연결 미확인일 때 추천과 축하가 멈추는 상태 표시',
    caps: [{ text: '차량 정보가 없을 때는 확인될 때까지 잠시 기다립니다' }],
  },
  {
    id: 'close',
    chapter: '다시 판단',
    from: 142,
    dur: 8,
    screen: 'WAYFILL parked home and logo',
    action: '정차 홈에서 로고와 마지막 한 줄만 남기며 종료',
    caps: [{ text: 'WAYFILL은 이렇게 주유할 곳과 때를 알아서 챙겨줍니다' }],
    audio: '배경 음악 마무리',
    dissolve: true,
  },
];

export const TOTAL_SECONDS = SCENES.reduce((sum, scene) => sum + scene.dur, 0);

if (TOTAL_SECONDS !== 150) {
  throw new Error(`WAYFILL edit must be 150 seconds, got ${TOTAL_SECONDS}`);
}

export const GLEO_REPLACEMENT: WayfillScene = {
  id: 'hun-choice-gleo',
  chapter: '주유 안내',
  from: 63,
  dur: 10,
  screen: 'navigation with WAYFILL HUN and verified Gleo input',
  action: '사용자가 응 그쪽으로 가자고 말하고 실제 WAYFILL 액션으로 전달되는 과정 표시',
  caps: [{ text: '말로 그쪽으로 가자고 해도 바로 안내를 시작합니다' }],
  audio: '실제 사용자 발화와 실제 인식음',
  verifiedOnly: 'gleo',
};
