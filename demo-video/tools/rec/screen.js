// Records the emulator display over gRPC as a constant 30 fps video, until SIGTERM.
// usage: node screen.js <out.mp4> <meta.json> [width height]
// The emulator sends a frame only when the screen changes. The newest frame is written to ffmpeg 30 times a
// second against the wall clock, so frame k of the file is the screen at startedAtMs + k / 30 s.
const fs = require('fs');
const path = require('path');
const { spawn } = require('child_process');
const grpc = require('@grpc/grpc-js');
const loader = require('@grpc/proto-loader');

const [out, metaPath, w = '1920', h = '1080'] = process.argv.slice(2);
const W = Number(w), H = Number(h), FPS = 30;
const lib = path.join(process.env.HOME, 'Library/Android/sdk/emulator/lib');
const def = loader.loadSync(path.join(lib, 'emulator_controller.proto'), { includeDirs: [lib], keepCase: true, longs: Number, enums: String });
const pkg = grpc.loadPackageDefinition(def).android.emulation.control;
// Our AVD runs gRPC on 8592; 8590 belongs to another project's emulator on this Mac.
const client = new pkg.EmulatorController(`localhost:${process.env.EMU_GRPC || 8592}`, grpc.credentials.createInsecure(), { 'grpc.max_receive_message_length': 128 * 1024 * 1024 });

const ff = spawn('ffmpeg', ['-hide_banner', '-loglevel', 'error', '-y', '-f', 'rawvideo', '-pix_fmt', 'rgb24', '-s', `${W}x${H}`,
  '-framerate', String(FPS), '-i', '-', '-c:v', 'h264_videotoolbox', '-b:v', '24M', '-pix_fmt', 'yuv420p', out], { stdio: ['pipe', 'inherit', 'inherit'] });

let latest = null;
let written = 0;
let startedAtMs = null;
let received = 0;
let stopping = false;
const call = client.streamScreenshot({ format: 'RGB888', width: W, height: H, display: 0 });
call.on('data', (img) => {
  if (img.image.length !== W * H * 3) return;
  latest = img.image;
  received += 1;
  if (startedAtMs === null) { startedAtMs = Date.now(); tick(); }
});
call.on('error', (e) => { if (e.code !== grpc.status.CANCELLED) console.error('screen error', e.message); });

function tick() {
  if (stopping) return;
  // Catch up to the wall clock, so a slow write never shifts later frames in time.
  const due = Math.floor(((Date.now() - startedAtMs) * FPS) / 1000) + 1;
  while (written < due) {
    if (!ff.stdin.write(latest)) { written += 1; ff.stdin.once('drain', tick); return; }
    written += 1;
  }
  setTimeout(tick, 1000 / FPS / 2);
}

const stop = () => {
  if (stopping) return;
  stopping = true;
  call.cancel();
  ff.stdin.end();
  ff.on('close', () => {
    fs.writeFileSync(metaPath, JSON.stringify({ startedAtMs, frames: written, received, fps: FPS, width: W, height: H }, null, 2));
    process.exit(0);
  });
};
process.on('SIGTERM', stop);
process.on('SIGINT', stop);
