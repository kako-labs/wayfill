import demo from '../data/demo.json';
import main from '../data/main.json';

/**
 * One take of the real app (tools/rec/wayfill_take.py) and one of its demo mode (demo_take.py) for the refuel that
 * needs a real fuel signal. `from` is seconds in the take; every scene plays at 1x. Rectangles are on the 2560x1440
 * Pleos display, read from uiautomator bounds or measured on the recording.
 */
export const FPS = 30;
export const CHAPTERS = ['출발 전', '주행', '경로 반영', '주유 후'];
export const ENDING_SEC = 10;

export type Rect = [number, number, number, number];
/** ttsAt: when the caption shows in the narrated version, if its line needs to start earlier than `at`. */
export type Caption = {at: number; title: string; voice?: boolean; ttsAt?: number};
export type Scene = {
  id: string;
  src: 'main' | 'demo';
  /** 1.. index CHAPTERS from 1 */
  chapter: number;
  from: number;
  dur: number;
  caps: Caption[];
  /** Seconds this scene fades in over the previous one. */
  dissolve?: number;
  spots?: {at: number; until: number; rect: Rect; dim?: boolean}[];
  /** A short label for inputs made on the emulator's Virtual Console. */
  note?: {at: number; until: number; text: string};
  /** Plays the take's recorded sound (the app's voice, the navigation's voice). */
  audio?: boolean;
  /** Demo mode from this many seconds into the scene: covers the demo's developer rows with a label. */
  demoFrom?: number;
};
export type Placed = Scene & {index: number; start: number};

const M = main.marks as Record<string, number>;
const D = demo.marks as Record<string, number>;
const CARD_AT = main.card?.t ?? M['card-wait'] + 3;

const CLUSTER: Rect = [0, 0, 853, 262];
const JUDGEMENT: Rect = [979, 341, 1645, 563];
const SUMMARY: Rect = [1672, 341, 2413, 528];
const MONITOR: Rect = [1672, 552, 2413, 752];
const HUN: Rect = [920, 40, 1640, 347];
const REROUTE: Rect = [877, 115, 1456, 965];
const NAV_ETA: Rect = [877, 960, 1456, 1275];
const MOVING_CARD: Rect = [877, 928, 1635, 1275];
const ON_ROUTE: Rect = [979, 347, 1645, 560];
const REFUEL: Rect = [1312, 715, 2072, 843];
const RECORD: Rect = [877, 461, 2510, 600];

const SCENES: Scene[] = [
  // Departure: parked, the navigation guides to 수원역; WAYFILL opens from the dock and needs no stop yet.
  {id: 'open', src: 'main', chapter: 1, from: M.open + 0.4, dur: 7, caps: [
    {at: 0, title: '내비에 수원역을 목적지로 정하고 하단 바에서 WAYFILL을 엽니다'},
  ]},
  // Starts once the first judgement is on screen (the home reads the car and the route for about 3 s).
  {id: 'home', src: 'main', chapter: 1, from: M.app + 4.0, dur: 10, dissolve: 0.3, spots: [{at: 0.5, until: 5.2, rect: SUMMARY}, {at: 5.4, until: 9.7, rect: JUDGEMENT}], caps: [
    {at: 0, title: '주행 가능 80km로 23km를 가도 50km 넘게 남습니다'},
    {at: 5.2, title: '그래서 지금은 중간에 주유하지 않아도 된다고 판단합니다'},
  ]},
  {id: 'monitor', src: 'main', chapter: 1, from: M.monitor - 0.6, dur: 6.4, spots: [{at: 1.4, until: 6.1, rect: MONITOR}], caps: [
    {at: 0, title: '모니터링을 켜 두면 화면을 닫아도 주행 중에 계속 판단합니다'},
  ]},

  // Driving: WAYFILL behind the navigation; the range drops until the reserve no longer holds; the card and voice.
  {id: 'nav', src: 'main', chapter: 2, from: M.nav - 0.4, dur: 5.4, dissolve: 0.3, caps: [
    {at: 0, title: '주행할 때는 내비를 앞에 두고 WAYFILL은 뒤에서 확인합니다'},
  ]},
  {id: 'drive', src: 'main', chapter: 2, from: M['gear-d'] - 0.3, dur: 6, spots: [{at: 0.6, until: 5.7, rect: CLUSTER, dim: false}],
    note: {at: 0.3, until: 5.8, text: 'Virtual Console 입력 · 기어 D · 60km/h'}, caps: [
      {at: 0, title: '기어를 D로 바꾸고 출발합니다'},
    ]},
  {id: 'drop', src: 'main', chapter: 2, from: M.drop - 0.4, dur: CARD_AT - M.drop - 0.1, spots: [{at: 0.5, until: CARD_AT - M.drop - 0.4, rect: CLUSTER, dim: false}],
    note: {at: 0.3, until: CARD_AT - M.drop - 0.3, text: 'Virtual Console 입력 · 주행 가능 80km → 70km'}, caps: [
      {at: 0, title: '에뮬레이터 내비는 움직이지 않아 줄어드는 주행 가능 거리를 콘솔로 넣습니다'},
      {at: 7.5, title: '주행 가능이 72km 아래로 내려가면 목적지에서 50km를 남길 수 없습니다'},
    ]},
  {id: 'card', src: 'main', chapter: 2, from: CARD_AT - 0.5, dur: M.guide - CARD_AT + 0.4, audio: true,
    spots: [{at: 0.6, until: M.guide - CARD_AT + 0.2, rect: HUN, dim: false}], caps: [
      {at: 0, title: '그 전에 여유 50km를 지키며 들를 수 있는 가장 먼 주유소를 내비 위에 알려 줍니다', voice: true},
    ]},

  // Route: "바로 안내" asks the navigation to add the station as a waypoint; WAYFILL confirms it took.
  {id: 'guide', src: 'main', chapter: 3, from: M.guide - 0.1, dur: 7.2, audio: true, caps: [
    {at: 0, title: '바로 안내를 누르면 그 주유소를 내비 경유지로 넣어 달라고 요청합니다'},
  ]},
  {id: 'reroute', src: 'main', chapter: 3, from: M.guide + 7.1, dur: 9, audio: true, spots: [{at: 0.4, until: 8.7, rect: REROUTE}], caps: [
    {at: 0, title: '내비가 미래주유소를 경유지로 넣어 경로를 다시 찾습니다'},
  ]},
  {id: 'waypoint', src: 'main', chapter: 3, from: M.waypoint + 0.3, dur: 6, spots: [{at: 0.5, until: 5.7, rect: NAV_ETA}], caps: [
    {at: 0, title: '이제 내비는 미래주유소를 거쳐 수원역으로 안내합니다'},
  ]},
  {id: 'app-again', src: 'main', chapter: 3, from: M['app-again'] - 0.3, dur: 7.6, spots: [{at: 2.2, until: 7.3, rect: MOVING_CARD}], caps: [
    {at: 0, title: 'WAYFILL을 다시 열면 내비가 경유지를 받아들였는지 확인해 보여 줍니다'},
  ]},
  {id: 'parked', src: 'main', chapter: 3, from: M.park + 2.6, dur: 7.5, dissolve: 0.4, spots: [{at: 0.8, until: 7.2, rect: ON_ROUTE}], caps: [
    {at: 0, title: '정차하면 같은 주유소를 다시 권하지 않고 안내 중이라고 알려 줍니다'},
  ]},

  // After refuelling: needs the car's fuel signal, which the electric emulator has not; shown in demo mode.
  {id: 'demo', src: 'demo', chapter: 4, from: D.demo - 0.2, dur: 7, dissolve: 0.5, demoFrom: 3.8, caps: [
    {at: 0, title: '주유 확인은 실차의 연료 신호가 필요해 시연 모드의 예시 데이터로 보여 줍니다'},
  ]},
  {id: 'refuel', src: 'demo', chapter: 4, from: D.refuel + 2.2, dur: 8.5, dissolve: 0.3, demoFrom: 0, spots: [{at: 1.0, until: 8.2, rect: REFUEL}], caps: [
    {at: 0, title: '주유가 끝나면 뽀동이가 한 번 반겨 주고 새 연료 상태로 다시 판단합니다'},
  ]},
  {id: 'records', src: 'demo', chapter: 4, from: D.records + 2.4, dur: 6.5, demoFrom: 0, spots: [{at: 0.8, until: 6.2, rect: RECORD}], caps: [
    {at: 0, title: '확인된 주유는 기록에 남습니다'},
  ]},
];

let cursor = 0;
export const PLACED: Placed[] = SCENES.map((s, index) => {
  const placed = {...s, index, start: cursor};
  cursor += s.dur;
  return placed;
});
export const MAIN_END = cursor;
export const END_SEC = MAIN_END + ENDING_SEC;

export const sceneAt = (sec: number) => {
  const scene = PLACED.find((p) => sec < p.start + p.dur) ?? PLACED[PLACED.length - 1];
  return {scene, local: sec - scene.start};
};

export const capAt = (c: Caption, narration = false) => (narration ? c.ttsAt ?? c.at : c.at);

export const captionAt = (scene: Placed, local: number, narration = false): Caption =>
  [...scene.caps].reverse().find((c) => local >= capAt(c, narration)) ?? scene.caps[0];
