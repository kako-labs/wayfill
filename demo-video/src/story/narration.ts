import narration from '../data/narration.json';
import {ENDING_SEC, MAIN_END, PLACED, capAt} from './scenes';

/**
 * The narrated version (WayfillStoryTts): each caption is read aloud as it appears (tools/tts/narrate.py makes the
 * clips from narration-lines.json). A line waits for the one before it to finish. Captions with `voice` are not read:
 * the car's own voice speaks there. Times are seconds in the body (after the title), like scenes.ts.
 */
type Clip = {text: string; sec: number};
const CLIPS = narration as Record<string, Clip>;

/** After a caption appears (and after a scene's dissolve) before its line starts. */
const LEAD = 0.2;
/** Between two lines when one has to wait for the other. */
const GAP = 0.25;

export type Line = {key: string; at: number; sec: number; text: string; lag: number; until: number};

export const TITLE_LINE_AT = 0.4;
export const TTS_TITLE_SEC = 6.0;
export const TITLE_LINE_SEC = CLIPS.title?.sec ?? 0;
/** The body starts 0.2 s before the title ends (Story.tsx); the title line may run on into the first scene. */
const titleFree = TITLE_LINE_AT + TITLE_LINE_SEC + GAP - (TTS_TITLE_SEC - 0.2);

const place = (): Line[] => {
  const lines: Line[] = [];
  let free = titleFree;
  for (const p of PLACED) {
    p.caps.forEach((c, i) => {
      if (c.voice) return;
      const key = `${p.id}.${i}`;
      const clip = CLIPS[key];
      if (!clip) return;
      const shown = p.start + capAt(c, true);
      const next = p.caps[i + 1];
      const until = next ? p.start + capAt(next, true) : p.start + p.dur;
      const at = Math.max(shown + LEAD + (i === 0 ? p.dissolve ?? 0 : 0), free);
      lines.push({key, at, sec: clip.sec, text: clip.text, lag: at - shown, until});
      free = at + clip.sec + GAP;
    });
  }
  let at = MAIN_END + 0.6;
  for (const key of ['ending.0', 'ending.1']) {
    const clip = CLIPS[key];
    if (!clip) continue;
    lines.push({key, at, sec: clip.sec, text: clip.text, lag: 0, until: Infinity});
    at += clip.sec + 0.35;
  }
  return lines;
};

export const LINES = place();

const endings = LINES.filter((l) => l.key.startsWith('ending.'));
const lastEnding = endings[endings.length - 1];
export const TTS_ENDING_SEC = Math.max(ENDING_SEC, lastEnding ? lastEnding.at + lastEnding.sec + 0.9 - MAIN_END : 0);
