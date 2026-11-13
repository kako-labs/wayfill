// Streams the emulator's audio output over gRPC into raw s16le stereo 44.1 kHz PCM.
// usage: node audio.js <out.pcm> <meta.json>   (stop with SIGTERM)
const fs = require('fs');
const path = require('path');
const grpc = require('@grpc/grpc-js');
const loader = require('@grpc/proto-loader');
const lib = path.join(process.env.HOME, 'Library/Android/sdk/emulator/lib');
const def = loader.loadSync(path.join(lib, 'emulator_controller.proto'), { includeDirs: [lib], keepCase: true, longs: Number, enums: String });
const pkg = grpc.loadPackageDefinition(def).android.emulation.control;
// Our AVD runs gRPC on 8592; 8590 belongs to another project's emulator on this Mac.
const client = new pkg.EmulatorController(`localhost:${process.env.EMU_GRPC || 8592}`, grpc.credentials.createInsecure(), { 'grpc.max_receive_message_length': 64 * 1024 * 1024 });
const out = fs.createWriteStream(process.argv[2]);
const meta = { startedAtMs: Date.now(), firstPacketAtMs: null, firstTimestampUs: null, bytes: 0, packets: 0 };
const call = client.streamAudio({ samplingRate: 44100, channels: 'Stereo', format: 'AUD_FMT_S16', mode: 'MODE_REAL_TIME' });
// Each record: 8-byte arrival time (µs, host epoch) + 4-byte length + PCM. Silence comes as no packets, and the
// packet timestamp only advances with audio played, so the assembler places packets by arrival time.
call.on('data', (p) => {
  if (meta.firstPacketAtMs === null) { meta.firstPacketAtMs = Date.now(); meta.firstTimestampUs = p.timestamp; }
  const head = Buffer.alloc(12);
  head.writeBigInt64LE(BigInt(Math.round((performance.timeOrigin + performance.now()) * 1000)), 0);
  head.writeUInt32LE(p.audio.length, 8);
  out.write(head); out.write(p.audio); meta.bytes += p.audio.length; meta.packets += 1;
});
call.on('error', (e) => { if (e.code !== grpc.status.CANCELLED) console.error('audio error', e.message); });
const stop = () => { call.cancel(); out.end(() => { fs.writeFileSync(process.argv[3], JSON.stringify(meta, null, 2)); process.exit(0); }); };
process.on('SIGTERM', stop); process.on('SIGINT', stop);
