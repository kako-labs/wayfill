"""Speaks the story narration with ElevenLabs, one clip per caption (src/story/narration-lines.json).

usage: python3 tools/tts/narrate.py [--force] [key ...]
A line is its text, or {"text": ..., "seed": n} when another seed read it more clearly (checked by transcribing the
clips with whisper; see README.md).
Needs ELEVENLABS_API_KEY and ELEVENLABS_VOICE_ID in the environment. Writes the API output to public/tts/raw/<key>.mp3,
a trimmed and loudness-matched copy to public/tts/<key>.mp3, and src/data/narration.json with each clip's text and
length. A clip whose text has not changed is kept, so only changed lines cost characters. --force or keys respeak.
The key is sent through curl's stdin config, never on the command line.
"""
import json
import os
import re
import subprocess
import sys
import tempfile

ROOT = os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
LINES = os.path.join(ROOT, "src", "story", "narration-lines.json")
OUT = os.path.join(ROOT, "public", "tts")
RAW = os.path.join(OUT, "raw")
DATA = os.path.join(ROOT, "src", "data", "narration.json")
MODEL = "eleven_multilingual_v2"
SEED = 7
LEAD, TAIL, PAUSE = 0.04, 0.08, 0.35


def speak(text, prev, nxt, dest, seed=SEED):
    key, voice = os.environ["ELEVENLABS_API_KEY"], os.environ["ELEVENLABS_VOICE_ID"]
    body = {"text": text, "model_id": MODEL, "seed": seed}
    # Neighbouring lines only shape the intonation; they are not spoken.
    if prev:
        body["previous_text"] = prev
    if nxt:
        body["next_text"] = nxt
    with tempfile.NamedTemporaryFile("w", suffix=".json", delete=False, encoding="utf-8") as f:
        json.dump(body, f, ensure_ascii=False)
        body_path = f.name
    url = f"https://api.elevenlabs.io/v1/text-to-speech/{voice}?output_format=mp3_44100_128"
    config = f'header = "xi-api-key: {key}"\nheader = "Content-Type: application/json"\n'
    try:
        res = subprocess.run(["curl", "-sS", "-m", "90", "-K", "-", "-X", "POST", "--data-binary", f"@{body_path}",
                              "-o", dest, "-w", "%{http_code}", url], input=config, capture_output=True, text=True)
    finally:
        os.unlink(body_path)
    if res.returncode != 0 or res.stdout.strip() != "200":
        detail = res.stderr
        if os.path.exists(dest):
            detail = open(dest, encoding="utf-8", errors="replace").read(400)
            os.unlink(dest)
        raise SystemExit(f"TTS failed ({res.stdout.strip()}): {detail}")


def silences(path, total):
    """Silent spans (below -40 dB for 0.12 s or more) as (start, end) seconds."""
    err = subprocess.run(["ffmpeg", "-hide_banner", "-i", path, "-af", "silencedetect=n=-40dB:d=0.12", "-f", "null", "-"],
                         capture_output=True, text=True).stderr
    starts = [float(x) for x in re.findall(r"silence_start: ([\d.]+)", err)]
    ends = [float(x) for x in re.findall(r"silence_end: ([\d.]+)", err)]
    return [(a, ends[i] if i < len(ends) else total) for i, a in enumerate(starts)]


def duration(path):
    out = subprocess.run(["ffprobe", "-v", "error", "-show_entries", "format=duration", "-of", "csv=p=0", path],
                         capture_output=True, text=True, check=True)
    return float(out.stdout.strip())


def finish(raw, dest):
    """Matches loudness, cuts the silence at both ends so a clip starts when it is placed, and shortens pauses inside a
    line to at most 0.35 s (the voice pauses 0.4-0.7 s at commas, which the scenes have no room for)."""
    with tempfile.TemporaryDirectory() as tmp:
        norm = os.path.join(tmp, "norm.wav")
        subprocess.run(["ffmpeg", "-y", "-loglevel", "error", "-i", raw, "-af", "loudnorm=I=-16:TP=-1.5:LRA=11",
                        "-ar", "44100", "-ac", "1", norm], check=True)
        total = duration(norm)
        keep, cursor = [], 0.0
        for a, b in silences(norm, total):
            if a <= 0.01:
                cursor = max(b - LEAD, 0.0)
                continue
            if b >= total - 0.01:
                keep.append((cursor, min(a + TAIL, total)))
                cursor = total
                break
            if b - a > PAUSE:
                keep.append((cursor, a + PAUSE / 2))
                cursor = b - PAUSE / 2
        if cursor < total:
            keep.append((cursor, total))
        parts = "".join(f"[0]atrim={a:.4f}:{b:.4f},asetpts=N/SR/TB[p{i}];" for i, (a, b) in enumerate(keep))
        graph = parts + "".join(f"[p{i}]" for i in range(len(keep))) + f"concat=n={len(keep)}:v=0:a=1[out]"
        subprocess.run(["ffmpeg", "-y", "-loglevel", "error", "-i", norm, "-filter_complex", graph, "-map", "[out]",
                        "-c:a", "libmp3lame", "-q:a", "2", dest], check=True)
    return round(duration(dest), 3)


def entry(value):
    return (value, SEED) if isinstance(value, str) else (value["text"], value.get("seed", SEED))


def main():
    args = sys.argv[1:]
    force = "--force" in args
    only = [a for a in args if not a.startswith("--")]
    lines = json.load(open(LINES, encoding="utf-8"))
    old = json.load(open(DATA, encoding="utf-8")) if os.path.exists(DATA) else {}
    os.makedirs(RAW, exist_ok=True)
    keys = list(lines)
    data = {}
    for i, key in enumerate(keys):
        text, seed = entry(lines[key])
        raw = os.path.join(RAW, f"{key}.mp3")
        dest = os.path.join(OUT, f"{key}.mp3")
        was = old.get(key, {})
        again = force or key in only or was.get("text") != text or was.get("seed", SEED) != seed or not os.path.exists(raw)
        if again:
            prev = entry(lines[keys[i - 1]])[0] if i > 0 else None
            nxt = entry(lines[keys[i + 1]])[0] if i + 1 < len(keys) else None
            speak(text, prev, nxt, raw, seed)
        data[key] = {"text": text, "seed": seed, "sec": finish(raw, dest)}
        print(f"{'spoke' if again else 'kept '} {key:12} {data[key]['sec']:5.2f}s  {text}", flush=True)
    json.dump(data, open(DATA, "w", encoding="utf-8"), ensure_ascii=False, indent=1)
    print("chars", sum(len(v["text"]) for v in data.values()))


if __name__ == "__main__":
    main()
