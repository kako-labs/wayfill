// Prints where each narration line plays and checks it against its caption and the recorded voices.
// usage: ./node_modules/.bin/esbuild tools/tts/placement.ts --bundle --platform=node --log-level=error | node
import main from '../../src/data/main.json';
import {LINES} from '../../src/story/narration';
import {PLACED} from '../../src/story/scenes';

// Recorded voices (the app's and the navigation's) in body time, for scenes that play the take's sound.
const voices = PLACED.filter((p) => p.audio).flatMap((p) =>
  main.clips.filter((c) => c.end > p.from && c.start < p.from + p.dur)
    .map((c) => ({scene: p.id, start: p.start + Math.max(c.start - p.from, 0), end: p.start + Math.min(c.end - p.from, p.dur)})));
let problems = 0;
for (const l of LINES) {
  const end = l.at + l.sec;
  const late = end > l.until + 0.05;
  const clash = voices.find((v) => l.at < v.end && end > v.start);
  if (late || clash) problems += 1;
  console.log(`${l.key.padEnd(12)} ${l.at.toFixed(2).padStart(7)}-${end.toFixed(2).padStart(7)} lag ${l.lag.toFixed(2)}`
    + `${late ? `  runs ${(end - l.until).toFixed(2)}s past its caption` : ''}${clash ? `  overlaps ${clash.scene} voice` : ''}`);
}
console.log('voices', voices.map((v) => `${v.scene} ${v.start.toFixed(2)}-${v.end.toFixed(2)}`).join(', '));
console.log(problems ? `${problems} line(s) to look at` : 'every line ends within its caption and clears the recorded voices');
