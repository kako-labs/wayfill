import React from 'react';
import {AbsoluteFill, Audio, Easing, Img, OffthreadVideo, Sequence, interpolate, staticFile, useCurrentFrame} from 'remotion';
import {THEME} from '../config/theme';
import demo from '../data/demo.json';
import main from '../data/main.json';
import {LINES, TITLE_LINE_AT, TITLE_LINE_SEC, TTS_ENDING_SEC, TTS_TITLE_SEC} from './narration';
import {CHAPTERS, END_SEC, ENDING_SEC, FPS, MAIN_END, PLACED, capAt, captionAt, sceneAt, type Placed, type Rect} from './scenes';

const clamp = {extrapolateLeft: 'clamp', extrapolateRight: 'clamp'} as const;
const ease = Easing.inOut(Easing.cubic);
const ramp = (t: number, a: number, b: number) => interpolate(t, [a, b], [0, 1], {...clamp, easing: ease});
const fontFace = `@font-face{font-family:'Noto Sans KR';src:url('${staticFile('fonts/NotoSansKR-VF.ttf')}');font-weight:100 900;}`;

/** The Pleos display (2560x1440) always sits in the same place at the same size; nothing moves the camera. */
const FRAME = {x: 176, y: 20, w: 1568, h: 882};
const K = FRAME.w / 2560;
const toFrame = ([x1, y1, x2, y2]: Rect) => ({left: x1 * K, top: y1 * K, width: (x2 - x1) * K, height: (y2 - y1) * K});

const SOURCES = {main: {video: 'rec/main_pleos.mp4', taps: main.taps}, demo: {video: 'rec/demo_pleos.mp4', taps: demo.taps}};
/** The demo's synthetic-data banner and developer buttons sit under the system bar, right of the driving view. */
const DEMO_ROWS: Rect = [853, 88, 2560, 234];

const SceneVideo = ({scene}: {scene: Placed}) => {
  const frame = useCurrentFrame();
  const local = frame / FPS - scene.start;
  const fadeIn = scene.dissolve ? ramp(local, 0, scene.dissolve) : 1;
  const extra = PLACED[scene.index + 1]?.dissolve ?? 0;
  return (
    <Sequence from={Math.round(scene.start * FPS)} durationInFrames={Math.round((scene.dur + extra) * FPS)} layout="none">
      <AbsoluteFill style={{opacity: fadeIn}}>
        <OffthreadVideo src={staticFile(SOURCES[scene.src].video)} trimBefore={Math.round(scene.from * FPS)} muted
          style={{width: FRAME.w, height: FRAME.h}} />
      </AbsoluteFill>
    </Sequence>
  );
};

/** Where the take tapped, drawn as a soft ring that fades out. */
const Taps = ({sec}: {sec: number}) => {
  const {scene, local} = sceneAt(sec);
  const src = scene.from + local;
  return (
    <>
      {SOURCES[scene.src].taps.filter((t) => src >= t.t && src < t.t + 0.8 && t.t >= scene.from).map((t) => {
        const age = src - t.t;
        const o = 1 - ramp(age, 0.25, 0.8);
        const r = 26 + 14 * ramp(age, 0, 0.8);
        return <div key={t.t} style={{position: 'absolute', left: t.x * K - r, top: t.y * K - r, width: r * 2, height: r * 2, borderRadius: '50%',
          background: `rgba(255,255,255,${0.32 * o})`, border: `2px solid rgba(255,255,255,${0.8 * o})`, boxShadow: `0 0 0 1px rgba(0,0,0,${0.25 * o})`}} />;
      })}
    </>
  );
};

/** A spot dims everything else a little and draws a thin outline; no glow, no blinking. */
const Spots = ({sec}: {sec: number}) => {
  const {scene, local} = sceneAt(sec);
  return (
    <>
      {(scene.spots ?? []).map((s, i) => {
        const o = Math.min(ramp(local, s.at, s.at + 0.4), 1 - ramp(local, s.until - 0.4, s.until));
        if (o <= 0) return null;
        return (
          <div key={i} style={{position: 'absolute', ...toFrame(s.rect), borderRadius: 12, opacity: o, border: `3px solid ${THEME.accent}`,
            boxShadow: s.dim === false ? 'none' : '0 0 0 4000px rgba(18,10,12,0.40)'}} />
        );
      })}
    </>
  );
};

const Note = ({sec}: {sec: number}) => {
  const {scene, local} = sceneAt(sec);
  if (!scene.note) return null;
  const o = Math.min(ramp(local, scene.note.at, scene.note.at + 0.4), 1 - ramp(local, scene.note.until - 0.4, scene.note.until));
  return (
    // Above the system bar, inside the driving view, so no control or caption is hidden.
    <div style={{position: 'absolute', left: 18, bottom: 92, opacity: o, fontSize: 21, fontWeight: 700, color: THEME.text,
      background: 'rgba(31,22,25,0.88)', border: `1px solid ${THEME.panelBorder}`, padding: '7px 16px', borderRadius: 18}}>
      {scene.note.text}
    </div>
  );
};

/** Demo mode only: hides the developer rows under a plain label, so the demo can never pass for the real car. */
const DemoCover = ({sec}: {sec: number}) => {
  const {scene, local} = sceneAt(sec);
  if (scene.demoFrom === undefined || local < scene.demoFrom) return null;
  return (
    <div style={{position: 'absolute', ...toFrame(DEMO_ROWS), background: '#3a2a2e', borderBottom: `1px solid ${THEME.panelBorder}`,
      display: 'flex', alignItems: 'center', paddingLeft: 22, fontSize: 21, fontWeight: 800, color: THEME.accent}}>
      시연 모드 · 예시 데이터 (실제 차량의 주유 신호가 아님)
    </div>
  );
};

const Band = ({sec, narration}: {sec: number; narration: boolean}) => {
  const {scene, local} = sceneAt(sec);
  const cap = captionAt(scene, local, narration);
  const textIn = ramp(local - capAt(cap, narration), 0, 0.35);
  return (
    <div style={{position: 'absolute', left: FRAME.x, width: FRAME.w, top: FRAME.y + FRAME.h + 18}}>
      <div style={{display: 'flex', alignItems: 'center', gap: 22, fontSize: 21, fontWeight: 600}}>
        {CHAPTERS.map((c, i) => (
          <span key={c} style={{color: i + 1 === scene.chapter ? THEME.accent : THEME.muted, opacity: i + 1 === scene.chapter ? 1 : 0.5,
            fontWeight: i + 1 === scene.chapter ? 800 : 500}}>{c}</span>
        ))}
        <span style={{marginLeft: 'auto', fontSize: 16, color: THEME.dim}}>
          {scene.demoFrom !== undefined && local >= scene.demoFrom
            ? 'WAYFILL 시연 모드 화면 · 예시 데이터' : 'Pleos Connect 에뮬레이터에서 녹화한 실제 WAYFILL 화면'}
        </span>
      </div>
      <div style={{opacity: textIn, transform: `translateY(${(1 - textIn) * 6}px)`}}>
        <div style={{fontSize: 38, fontWeight: 800, color: THEME.text, marginTop: 10, display: 'flex', alignItems: 'center', gap: 16}}>
          {cap.title}
          {cap.voice ? <span style={{fontSize: 19, fontWeight: 700, color: '#3a2412', background: THEME.accent, borderRadius: 14, padding: '3px 12px', whiteSpace: 'nowrap'}}>차량 음성</span> : null}
        </div>
      </div>
    </div>
  );
};

const Main = ({sec, narration}: {sec: number; narration: boolean}) => (
  <AbsoluteFill>
    <div style={{position: 'absolute', left: FRAME.x, top: FRAME.y, width: FRAME.w, height: FRAME.h, borderRadius: 12, overflow: 'hidden',
      background: '#000', boxShadow: '0 18px 50px rgba(0,0,0,0.5)', border: `1px solid ${THEME.panelBorder}`}}>
      {PLACED.map((p) => <SceneVideo key={p.id} scene={p} />)}
      <DemoCover sec={sec} />
      <Spots sec={sec} />
      <Taps sec={sec} />
      <Note sec={sec} />
    </div>
    <Band sec={sec} narration={narration} />
  </AbsoluteFill>
);

const LOOP = ['출발 전 연료 판단', '주행 중 HUN과 음성', '내비 경유지 추가', '주유 후 다시 판단'];
const USED = ['주행 가능 거리', '기어·속도', '내비 경로와 경유지', '알림 카드(HUN)', '차량 음성 안내'];

const Ending = ({sec}: {sec: number}) => {
  const a = ramp(sec, 0.2, 1.0);
  return (
    <AbsoluteFill style={{alignItems: 'center', justifyContent: 'center', textAlign: 'center', opacity: a}}>
      <div style={{display: 'flex', alignItems: 'center', gap: 28, marginTop: -40}}>
        <Img src={staticFile('story/app_icon.png')} style={{width: 120, height: 120}} />
        <div style={{textAlign: 'left'}}>
          <div style={{fontSize: 84, fontWeight: 900, color: THEME.text, letterSpacing: -1}}>WAYFILL</div>
          <div style={{fontSize: 32, fontWeight: 500, color: THEME.muted}}>주유하기 좋은 마지막 기회를 놓치기 전에</div>
        </div>
      </div>
      <div style={{display: 'flex', alignItems: 'center', gap: 16, marginTop: 52, opacity: ramp(sec, 1.0, 1.8)}}>
        {LOOP.map((f, i) => (
          <React.Fragment key={f}>
            {i > 0 ? <span style={{fontSize: 28, color: THEME.accent}}>→</span> : null}
            <span style={{fontSize: 27, fontWeight: 700, color: THEME.text, padding: '12px 22px', borderRadius: 14, background: THEME.panel,
              border: `1px solid ${THEME.panelBorder}`}}>{f}</span>
          </React.Fragment>
        ))}
      </div>
      <div style={{marginTop: 40, opacity: ramp(sec, 2.2, 3.0)}}>
        <div style={{fontSize: 21, fontWeight: 700, color: THEME.accent}}>영상에서 쓴 Pleos 차량 기능</div>
        <div style={{fontSize: 26, color: THEME.text, marginTop: 10}}>{USED.join('  ·  ')}</div>
        <div style={{fontSize: 21, color: THEME.muted, marginTop: 12}}>주유소 가격·위치: 오피넷  ·  경로와 우회 시간: 카카오모빌리티 길찾기</div>
      </div>
      <div style={{position: 'absolute', bottom: 40, fontSize: 18.5, color: THEME.dim, lineHeight: 1.7, opacity: ramp(sec, 2.2, 3.0)}}>
        실제 앱을 Pleos Connect 에뮬레이터에서 녹화했습니다. 기어·속도·주행 가능 거리는 Virtual Console로 입력했고, 실차에서는 확인하지 않았습니다.<br />
        에뮬레이터 내비는 위치가 움직이지 않아 주행으로 줄어드는 거리를 콘솔로 넣었습니다. 에뮬레이터 차량이 전기차라 연료는 설정에서 휘발유로 골랐고,<br />
        주유 확인과 기록 장면은 시연 모드의 예시 데이터입니다.
      </div>
    </AbsoluteFill>
  );
};

/** A calm title before the car screen: the app's icon, name and one line. No glow, no feature montage. */
export const TITLE_SEC = 3.6;

const Title = ({sec, len}: {sec: number; len: number}) => {
  const app = Math.min(ramp(sec, 0.2, 0.8), 1 - ramp(sec, len - 0.4, len + 0.2));
  return (
    <AbsoluteFill style={{alignItems: 'center', justifyContent: 'center', textAlign: 'center'}}>
      <div style={{display: 'flex', alignItems: 'center', gap: 28, opacity: app}}>
        <Img src={staticFile('story/app_icon.png')} style={{width: 132, height: 132}} />
        <div style={{textAlign: 'left'}}>
          <div style={{fontSize: 88, fontWeight: 900, color: THEME.text, letterSpacing: -1}}>WAYFILL</div>
          <div style={{fontSize: 32, fontWeight: 500, color: THEME.muted}}>주유하기 좋은 마지막 기회를 놓치기 전에 알려 주는 Pleos 차량용 앱</div>
        </div>
      </div>
    </AbsoluteFill>
  );
};

/** Everything after the title, timed from its own start (scenes.ts times). */
const Body = ({narration}: {narration: boolean}) => {
  const sec = useCurrentFrame() / FPS;
  const mainIn = ramp(sec, 0, 0.8);
  const mainOut = 1 - ramp(sec, MAIN_END - 0.8, MAIN_END);
  return (
    <AbsoluteFill>
      {sec < MAIN_END ? <AbsoluteFill style={{opacity: Math.min(mainIn, mainOut)}}><Main sec={sec} narration={narration} /></AbsoluteFill> : null}
      {sec >= MAIN_END - 0.2 ? <Ending sec={sec - MAIN_END} /> : null}
      {PLACED.filter((p) => p.audio).map((p) => {
        const frames = Math.round(p.dur * FPS);
        return (
          <Sequence key={p.id} from={Math.round(p.start * FPS)} durationInFrames={frames} layout="none">
            <Audio src={staticFile('rec/main_audio.wav')} trimBefore={Math.round(p.from * FPS)}
              volume={(f) => interpolate(f, [0, 6, frames - 6, frames], [0, 1, 1, 0], clamp)} />
          </Sequence>
        );
      })}
      {narration ? LINES.map((l) => (
        // The clip's own length, so no line is cut by a scene or the title.
        <Sequence key={l.key} from={Math.round(l.at * FPS)} durationInFrames={Math.ceil((l.sec + 0.1) * FPS)} layout="none">
          <Audio src={staticFile(`tts/${l.key}.mp3`)} />
        </Sequence>
      )) : null}
    </AbsoluteFill>
  );
};

/** narration: the version with the captions read aloud (narration.ts); the title and ending hold for their lines. */
export const Story = ({narration = false}: {narration?: boolean}) => {
  const sec = useCurrentFrame() / FPS;
  const titleSec = narration ? TTS_TITLE_SEC : TITLE_SEC;
  return (
    <AbsoluteFill style={{background: THEME.bg, fontFamily: THEME.font}}>
      <style>{fontFace}</style>
      <AbsoluteFill style={{background: 'radial-gradient(1000px 640px at 18% 0%, rgba(255,170,110,0.16), transparent 70%)'}} />
      {sec < titleSec + 0.3 ? <Title sec={sec} len={titleSec} /> : null}
      {narration ? (
        <Sequence from={Math.round(TITLE_LINE_AT * FPS)} durationInFrames={Math.ceil((TITLE_LINE_SEC + 0.1) * FPS)} layout="none">
          <Audio src={staticFile('tts/title.mp3')} />
        </Sequence>
      ) : null}
      <Sequence from={Math.round((titleSec - 0.2) * FPS)} layout="none">
        <Body narration={narration} />
      </Sequence>
    </AbsoluteFill>
  );
};

export const STORY_FRAMES = Math.round((TITLE_SEC - 0.2 + END_SEC) * FPS);
export const STORY_TTS_FRAMES = Math.round((TTS_TITLE_SEC - 0.2 + END_SEC - ENDING_SEC + TTS_ENDING_SEC) * FPS);
export {ENDING_SEC};
