"""Transcribes the narration clips with whisper and lists the ones that do not read back as their line.

usage: python3 tools/tts/check.py [key ...]
       python3 tools/tts/check.py --video out/motioncare-story-tts.mp4
The second form transcribes the rendered video and checks that every line is heard to its end, in order; a clip that
reads well can still be cut short in the edit (the title once was).
Needs the openai-whisper command (`whisper`) with the medium model. A mismatch is a hint, not a verdict: whisper also
mishears, writes numbers as digits and spells sounds (싸인 for 쌓인), so listen to each flagged clip.
"""
import json
import os
import subprocess
import sys
import tempfile

ROOT = os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
LINES = os.path.join(ROOT, "src", "story", "narration-lines.json")
CLIPS = os.path.join(ROOT, "public", "tts")
# Readings written as they sound, mapped back so a correct clip matches.
SPOKEN = {"십 분": "10분", "삼 분": "3분", "삼 회": "3회", "플레 오스": "플레오스", "바 에서": "바에서"}


def norm(s):
    return "".join(ch for ch in s if ch.isalnum())


def spoken_text(value):
    text = value if isinstance(value, str) else value["text"]
    for a, b in SPOKEN.items():
        text = text.replace(a, b)
    return text


def check_video(path, lines):
    with tempfile.TemporaryDirectory() as tmp:
        wav = os.path.join(tmp, "audio.wav")
        subprocess.run(["ffmpeg", "-y", "-loglevel", "error", "-i", path, "-vn", "-ac", "1", "-ar", "16000", wav], check=True)
        subprocess.run(["whisper", wav, "--model", "medium", "--language", "ko", "--output_dir", tmp, "--output_format", "txt",
                        "--fp16", "False"], capture_output=True, check=True)
        heard = norm(open(os.path.join(tmp, "audio.txt"), encoding="utf-8").read()).replace("피", "P")
    pos, bad = 0, 0
    for k, value in lines.items():
        end = norm(spoken_text(value)).replace("피", "P")[-6:]
        at = heard.find(end, pos)
        if at < 0:
            bad += 1
            print(f"≠ {k:14} end not heard: {end}")
        else:
            pos = at + len(end)
    print(f"{bad} of {len(lines)} lines not heard to their end")


def main():
    lines = json.load(open(LINES, encoding="utf-8"))
    if sys.argv[1:2] == ["--video"]:
        check_video(sys.argv[2], lines)
        return
    keys = sys.argv[1:] or list(lines)
    with tempfile.TemporaryDirectory() as tmp:
        subprocess.run(["whisper", *[os.path.join(CLIPS, f"{k}.mp3") for k in keys], "--model", "medium", "--language", "ko",
                        "--output_dir", tmp, "--output_format", "txt", "--fp16", "False"], capture_output=True, check=True)
        bad = 0
        for k in keys:
            text = spoken_text(lines[k])
            got = " ".join(open(os.path.join(tmp, f"{k}.txt"), encoding="utf-8").read().split())
            same = norm(got).replace("피", "P") == norm(text).replace("피", "P")
            bad += not same
            print(f"{'  ' if same else '≠ '}{k:14} {got}" + ("" if same else f"\n{'':17}line: {text}"))
        print(f"{bad} of {len(keys)} differ")


if __name__ == "__main__":
    main()
