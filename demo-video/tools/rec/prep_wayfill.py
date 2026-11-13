"""Lines up the WAYFILL takes for the Remotion edit.

usage: python3 tools/rec/prep_wayfill.py <main take> <demo take>
Time zero of each take is the first frame of its screen recording (screen.json). Writes public/rec/main_pleos.mp4,
public/rec/main_audio.wav, public/rec/demo_pleos.mp4, src/data/main.json and src/data/demo.json (marks, taps,
console inputs, the card, and the voice clips with their start and end in seconds).

Sound: audio packets carry their arrival time on the host and arrive after they play. The delay is measured on this
take from the speech engine's "writeAudioTrack ... start" log lines (emulator clock moved to the host clock with the
offset measured during the take, clock.json) against the first loud packet that follows each.
"""
import json
import os
import re
import statistics
import struct
import subprocess
import sys
import wave

ROOT = os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
PUB = os.path.join(ROOT, "public", "rec")
DATA = os.path.join(ROOT, "src", "data")
os.makedirs(PUB, exist_ok=True)
os.makedirs(DATA, exist_ok=True)
MAIN, DEMO = (os.path.abspath(p) for p in sys.argv[1:3])
FPS = 30
RATE, FRAME = 44100, 4


def run(cmd):
    print("$", " ".join(cmd)[:160], flush=True)
    subprocess.run(cmd, check=True)


def reencode(take, name):
    # Short keyframe distance so Remotion can seek anywhere quickly.
    run(["ffmpeg", "-y", "-loglevel", "error", "-i", os.path.join(take, "screen.mp4"), "-c:v", "libx264", "-crf", "16",
         "-preset", "medium", "-g", "15", "-pix_fmt", "yuv420p", "-movflags", "+faststart", os.path.join(PUB, f"{name}_pleos.mp4")])


def base(take):
    meta = json.load(open(os.path.join(take, "screen.json")))
    events = json.load(open(os.path.join(take, "events.json")))
    t0 = meta["startedAtMs"]
    sec = lambda ms: round((ms - t0) / 1000, 3)  # noqa: E731
    return meta, events, t0, sec, {
        "duration": meta["frames"] / FPS,
        "marks": {e["name"]: sec(e["t"]) for e in events if e["kind"] == "mark"},
        # Taps are on the 2560x1440 display.
        "taps": [{"t": sec(e["t"]), "x": e["x"], "y": e["y"], "text": e["text"]} for e in events if e["kind"] == "tap"],
        "missing": [e["text"] for e in events if e["kind"] == "missing"],
    }


# Main take: video, aligned sound and the console inputs.
meta, events, t0, sec, main = base(MAIN)
reencode(MAIN, "main")
offset = statistics.median(json.load(open(os.path.join(MAIN, "clock.json")))["emulatorMinusHostMs"])
starts = []
for line in open(os.path.join(MAIN, "logcat.txt"), errors="replace"):
    m = re.match(r"\s*(\d+\.\d+)\s+\d+\s+\d+\s+\w\s+.*writeAudioTrack\((\S+)\) - start", line)
    if m:
        starts.append({"t": int(float(m.group(1)) * 1000) - offset, "app": m.group(2)})

raw = open(os.path.join(MAIN, "audio.rec"), "rb").read()
packets, i = [], 0
while i + 12 <= len(raw):
    ts, n = struct.unpack_from("<qI", raw, i)
    i += 12
    packets.append((ts // 1000, raw[i:i + n]))
    i += n


def loud(chunk, level=600):
    vals = struct.unpack(f"<{len(chunk) // 2}h", chunk[: len(chunk) // 2 * 2])
    return max((abs(v) for v in vals), default=0) >= level


lags = []
for s in starts:
    hits = [ts for ts, ch in packets if s["t"] - 100 <= ts <= s["t"] + 3000 and loud(ch)]
    if hits:
        lags.append(hits[0] - s["t"])
lag = int(statistics.median(lags)) if lags else 450
print("speech starts", [(s["app"], sec(s["t"])) for s in starts], "lags", lags, "using", lag)

dur_ms = int(meta["frames"] / FPS * 1000)
total = int(dur_ms / 1000 * RATE) * FRAME
pcm = bytearray(total)
for ts, chunk in packets:
    end_frame = int((ts - lag - t0) / 1000 * RATE)
    off = (end_frame - len(chunk) // FRAME) * FRAME
    if 0 <= off and off + len(chunk) <= total:
        pcm[off:off + len(chunk)] = chunk
aligned = os.path.join(MAIN, "audio_aligned.wav")
with wave.open(aligned, "wb") as w:
    w.setnchannels(2)
    w.setsampwidth(2)
    w.setframerate(RATE)
    w.writeframes(bytes(pcm))
# The emulator plays quietly; lift it and keep peaks below clipping.
run(["ffmpeg", "-y", "-loglevel", "error", "-i", aligned, "-af", "volume=20dB,alimiter=limit=0.9", os.path.join(PUB, "main_audio.wav")])

# Voice clips: loud stretches of the aligned sound, merged across short pauses.
clips = []
for ts, chunk in packets:
    if not loud(chunk):
        continue
    t = (ts - lag - t0) / 1000
    if clips and t - clips[-1]["end"] < 1.2:
        clips[-1]["end"] = round(t, 2)
    else:
        clips.append({"start": round(t - len(chunk) / FRAME / RATE, 2), "end": round(t, 2)})
main.update({
    "clockOffsetMs": offset,
    "audioLagMs": lag,
    "speech": [{"t": sec(s["t"] + lag), "app": s["app"]} for s in starts],
    "clips": clips,
    "card": next(({"t": sec(e["t"]), "text": e.get("text")} for e in events if e["kind"] == "card"), None),
    "console": [{"t": sec(e["t"]), **{k: v for k, v in e.items() if k not in ("t", "kind", "status")}} for e in events if e["kind"] == "console"],
})
json.dump(main, open(os.path.join(DATA, "main.json"), "w"), ensure_ascii=False, indent=1)
print("main", main["duration"], "s", main["marks"], "card", main["card"], "clips", clips, "missing", main["missing"])

# Demo take: video and marks only.
_, _, _, _, demo = base(DEMO)
reencode(DEMO, "demo")
json.dump(demo, open(os.path.join(DATA, "demo.json"), "w"), ensure_ascii=False, indent=1)
print("demo", demo)
