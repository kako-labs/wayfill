"""Builds the WAYFILL screen review page: the demo video and every captured screen with notes (shots.py).

usage: python3 tools/gallery/build.py <raw_dir> <final_dir> <site_dir>
raw_dir holds the 2560x1440 / 1920x1080 PNGs from tools/rec/capture_states.py and the story frames; final_dir the
rendered videos (wayfill-story*.mp4 and their -720p previews). Writes site_dir/index.html, img/*.jpg (1600x900; the
demo mode's developer rows covered by the same label the video uses) and video/*. Needs ffmpeg and Google Chrome.
"""
import html
import json
import os
import shutil
import subprocess
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import shots as S  # noqa: E402

CHROME = "/Applications/Google Chrome.app/Contents/MacOS/Google Chrome"
DEMO_LABEL = "시연 모드 · 예시 데이터 (실제 차량의 주유 신호가 아님)"
# The demo mode's developer rows on the 2560x1440 display (src/story/Story.tsx DEMO_ROWS).
DEMO_ROWS = (853, 88, 1707, 146)
NARRATION_SHIFT = S.VIDEOS[1]["chapters"][0] - S.VIDEOS[0]["chapters"][0]


def e(text):
    return html.escape(text, quote=True)


def mmss(sec):
    sec = int(round(sec))
    return f"{sec // 60}:{sec % 60:02d}"


def label_png(work):
    x, y, w, h = DEMO_ROWS
    page = os.path.join(work, "label.html")
    with open(page, "w", encoding="utf-8") as f:
        f.write(f"""<!doctype html><meta charset="utf-8"><style>html,body{{margin:0;width:{w}px;height:{h}px;overflow:hidden}}
div{{box-sizing:border-box;width:{w}px;height:{h}px;background:#3a2a2e;border-bottom:2px solid #8a7060;display:flex;align-items:center;
padding-left:36px;font:800 36px 'Apple SD Gothic Neo',sans-serif;color:#FFDA91}}</style><div>{e(DEMO_LABEL)}</div>""")
    out = os.path.join(work, "label.png")
    subprocess.run([CHROME, "--headless=new", "--disable-gpu", "--hide-scrollbars", "--force-device-scale-factor=1",
                    f"--window-size={w},{h}", f"--screenshot={out}", "file://" + page], capture_output=True, check=True)
    return out


def images(raw, site):
    os.makedirs(os.path.join(site, "img"), exist_ok=True)
    label = label_png(site)
    for name in sorted(os.listdir(raw)):
        if not name.endswith(".png"):
            continue
        out = os.path.join(site, "img", name[:-4] + ".jpg")
        if name.startswith("d-"):
            args = ["-i", os.path.join(raw, name), "-i", label, "-filter_complex",
                    f"[0][1]overlay={DEMO_ROWS[0]}:{DEMO_ROWS[1]},scale=1600:900"]
        else:
            args = ["-i", os.path.join(raw, name), "-vf", "scale=1600:900"]
        subprocess.run(["ffmpeg", "-loglevel", "error", "-y", *args, "-q:v", "3", out], check=True)
    for tmp in ("label.html", "label.png"):
        os.remove(os.path.join(site, tmp))


def videos(final, site):
    os.makedirs(os.path.join(site, "video"), exist_ok=True)
    for v in S.VIDEOS:
        shutil.copyfile(os.path.join(final, os.path.basename(v["file"])), os.path.join(site, v["file"]))
    subprocess.run(["ffmpeg", "-loglevel", "error", "-y", "-ss", "12", "-i", os.path.join(final, "wayfill-story.mp4"),
                    "-frames:v", "1", "-vf", "scale=1280:720", "-q:v", "3", os.path.join(site, "video", "poster.jpg")], check=True)


def tag_demo(shot):
    return '<span class="tag tag-demo">시연 모드</span>' if shot.get("demo") or shot["file"].startswith("d-") else '<span class="tag">실제 앱</span>'


def story_html():
    out, n = [], 0
    for ci, ch in enumerate(S.CHAPTERS):
        out.append(f'<section class="chapter" id="{ch["id"]}" aria-labelledby="{ch["id"]}-h">'
                   f'<header class="chapter-head"><p class="eyebrow">장면 {ci + 1} / {len(S.CHAPTERS)}</p>'
                   f'<h2 id="{ch["id"]}-h">{e(ch["title"])}</h2><p class="chapter-lead">{e(ch["lead"])}</p></header>')
        for shot in [s for s in S.STORY if s["chapter"] == ch["id"]]:
            n += 1
            video = (f'<button class="tc" type="button" data-seek="{shot["video"]}">영상 {mmss(shot["video"])}</button>'
                     if shot["video"] is not None else '<span class="tc tc-off">영상에 없음</span>')
            rows = [("상황", shot["state"], ""), ("할 수 있는 것", shot["can"], "")]
            if shot.get("changed"):
                rows.append(("이번에 바뀐 점", shot["changed"], "changed"))
            if shot.get("look"):
                rows.append(("살펴볼 점", shot["look"], "look"))
            dl = "".join(f'<div class="row {cls}"><dt>{e(k)}</dt><dd>{e(v)}</dd></div>' for k, v, cls in rows)
            out.append(f'''<article class="shot" id="{shot["file"]}">
  <button class="frame" type="button" data-full="img/{shot["file"]}.jpg" data-caption="화면 {n} · {e(shot["title"])}" aria-label="화면 {n} 크게 보기">
    <img src="img/{shot["file"]}.jpg" width="1600" height="900" alt="{e(shot["title"])}" {"" if n <= 2 else 'loading="lazy"'}></button>
  <div class="notes">
    <p class="shot-meta"><span class="shot-no">화면 {n}</span>{video}{tag_demo(shot)}</p>
    <h3>{e(shot["title"])}</h3>
    <dl>{dl}</dl>
    <p class="file">{shot["file"]}.png</p>
  </div>
</article>''')
        out.append("</section>")
    return "\n".join(out)


def card(shot):
    look = f'<p class="card-look"><b>살펴볼 점</b> {e(shot["look"])}</p>' if shot.get("look") else ""
    note = f'<p class="card-note">{e(shot["note"])}</p>' if shot.get("note") else ""
    return f'''<figure class="card" id="{shot["file"]}">
  <button class="frame" type="button" data-full="img/{shot["file"]}.jpg" data-caption="{e(shot["title"])}" aria-label="{e(shot["title"])} 크게 보기">
    <img src="img/{shot["file"]}.jpg" width="1600" height="900" alt="{e(shot["title"])}" loading="lazy"></button>
  <figcaption><h4>{e(shot["title"])}</h4><p>{e(shot["state"])}</p>{look}{note}<p class="file">{shot["file"]}.png</p></figcaption>
</figure>'''


def gallery_html():
    real = "\n".join(card(s) for s in S.REAL)
    demo = "\n".join(f'<h3 class="group-title">{e(g["title"])} <span>{len(g["shots"])}</span></h3><div class="grid">'
                     + "\n".join(card(s) for s in g["shots"]) + "</div>" for g in S.DEMO_GROUPS)
    return real, demo


def looks_html():
    return "\n".join(f'<li><a href="#{f}"><span class="look-what">{e(what)}</span><span class="look-why">{e(why)}</span>'
                     f'<span class="file">{f}.png</span></a></li>' for f, what, why in S.LOOKS)


def page():
    real, demo = gallery_html()
    cuts = [{**v, "chapters": v["chapters"]} for v in S.VIDEOS]
    chapter_names = [c["title"] for c in S.CHAPTERS] + ["마무리"]
    chapter_buttons = "".join(f'<li><button type="button" data-chapter="{i}"><span>{e(name)}</span>'
                              f'<span class="ch-time">{mmss(S.VIDEOS[0]["chapters"][i])}</span></button></li>'
                              for i, name in enumerate(chapter_names))
    story_count = len(S.STORY)
    real_count = len([s for s in S.STORY if not s.get("demo")]) + len(S.REAL)
    demo_count = sum(len(g["shots"]) for g in S.DEMO_GROUPS)
    disclosures = "".join(f"<li>{e(d)}</li>" for d in S.DISCLOSURES)
    with open(os.path.join(os.path.dirname(os.path.abspath(__file__)), "page.css"), encoding="utf-8") as f:
        css = f.read()
    with open(os.path.join(os.path.dirname(os.path.abspath(__file__)), "page.js"), encoding="utf-8") as f:
        js = f.read().replace("__CUTS__", json.dumps(cuts, ensure_ascii=False)).replace("__SHIFT__", f"{NARRATION_SHIFT:.1f}")
    return f'''<title>WAYFILL 화면 리뷰</title>
<meta name="claude:viewport-fit-sides" content="cover">
<link rel="preconnect" href="https://fonts.googleapis.com">
<link rel="preconnect" href="https://fonts.gstatic.com" crossorigin>
<link rel="stylesheet" href="https://fonts.googleapis.com/css2?family=IBM+Plex+Mono:wght@400;500&family=Noto+Sans+KR:wght@400;500;700;900&display=swap">
<style>
{css}
</style>
<div class="page">
<header class="masthead">
  <p class="eyebrow">Pleos Connect 앱 공모전 · 시연 영상과 화면</p>
  <h1>WAYFILL 화면 리뷰</h1>
  <p class="lead">주유하기 좋은 마지막 기회를 놓치기 전에 알려 주는 Pleos 차량용 앱입니다. 시연 영상과, 영상에 나온 장면 {story_count}개, 영상에 나오지 않는 상태까지 앱의 모든 화면을 장면 순서대로 모았습니다.</p>
  <ul class="chips">
    <li>2026년 11월 14일 촬영</li><li>Pleos Connect 에뮬레이터 · 2560×1440</li>
    <li>실제 앱 {real_count}장</li><li class="chip-demo">시연 모드 {demo_count}장</li><li>판교 → 수원역 23km · 여유 50km</li>
  </ul>
  <p class="comment-hint">화면이나 문구에 대한 의견은 이 페이지에 댓글로 남겨 주세요. 이미지를 누르면 크게 보고 ← → 키로 넘길 수 있습니다.</p>
</header>
<nav class="toc" aria-label="장면 이동"><div class="toc-inner">
  <a href="#video">영상</a><a href="#looks">살펴볼 점</a>{"".join(f'<a href="#{c["id"]}">{e(c["title"])}</a>' for c in S.CHAPTERS)}<a href="#real">다른 상황</a><a href="#demo">시연 모드</a><a href="#method">촬영 방법</a>
</div></nav>

<section class="video" id="video" aria-labelledby="video-h">
  <div class="video-main">
    <div class="player"><video id="player" controls preload="metadata" playsinline poster="video/poster.jpg" src="{S.VIDEOS[0]["file"]}"></video></div>
  </div>
  <aside class="video-side">
    <h2 id="video-h">시연 영상</h2>
    <div class="cut-switch" role="group" aria-label="영상 버전">
      {"".join(f'<button type="button" id="cut-{v["id"]}" data-cut="{i}" aria-pressed="{"true" if i == 0 else "false"}">{e(v["label"])} <span>{v["length"]}</span></button>' for i, v in enumerate(S.VIDEOS))}
    </div>
    <p class="cut-note" id="cut-note">{e(S.VIDEOS[0]["note"])}</p>
    <ol class="chapters">{chapter_buttons}</ol>
    <p class="video-foot">미리보기는 720p입니다. 제출용 원본은 1080p·30fps입니다.</p>
  </aside>
</section>

<section class="looks" id="looks" aria-labelledby="looks-h">
  <h2 id="looks-h">살펴볼 점 <span class="count">{len(S.LOOKS)}</span></h2>
  <p class="section-lead">촬영하며 눈에 띈 문구와 데이터입니다. 영상 흐름에는 영향이 없고, 제출 전에 고칠지 정하면 됩니다.</p>
  <ol class="look-list">{looks_html()}</ol>
</section>

{story_html()}

<section class="gallery" id="real" aria-labelledby="real-h">
  <header class="chapter-head"><p class="eyebrow">영상 밖의 상태 · 실제 앱</p><h2 id="real-h">다른 상황</h2>
  <p class="chapter-lead">같은 에뮬레이터에서 기어·주행 가능 거리·목적지·권한을 바꿔 가며 찍었습니다.</p></header>
  <div class="grid">{real}</div>
</section>

<section class="gallery gallery-demo" id="demo" aria-labelledby="demo-h">
  <header class="chapter-head"><p class="eyebrow">예시 데이터</p><h2 id="demo-h">시연 모드</h2>
  <p class="chapter-lead">실차 신호 없이 모든 판단 결과를 보여 주는 모드입니다. 홈의 '시연 모드 · 예시 데이터'로 들어가며, 화면 위쪽 개발용 버튼 줄은 라벨로 가렸습니다.</p></header>
  {demo}
</section>

<section class="method" id="method" aria-labelledby="method-h">
  <h2 id="method-h">촬영 방법과 공개 사항</h2>
  <ul>{disclosures}</ul>
  <p class="method-foot">영상 구성: 사용자 여정 순서, 화면 고정, 확대·효과·배속 없음, 자막 한 문장과 장면 표시줄, 끝에 공개 사항.</p>
</section>
</div>

<div class="lightbox" id="lightbox" role="dialog" aria-modal="true" aria-label="화면 크게 보기" hidden>
  <figure><img id="lb-img" alt=""><figcaption><span id="lb-cap"></span><span id="lb-count" class="lb-count"></span></figcaption></figure>
  <button type="button" class="lb-btn lb-prev" id="lb-prev" aria-label="이전 화면">←</button>
  <button type="button" class="lb-btn lb-next" id="lb-next" aria-label="다음 화면">→</button>
  <button type="button" class="lb-btn lb-close" id="lb-close" aria-label="닫기">✕</button>
</div>
<script>
{js}
</script>
'''


def main():
    raw, final, site = (os.path.abspath(a) for a in sys.argv[1:4])
    os.makedirs(site, exist_ok=True)
    images(raw, site)
    videos(final, site)
    with open(os.path.join(site, "index.html"), "w", encoding="utf-8") as f:
        f.write(page())
    print("wrote", os.path.join(site, "index.html"))


if __name__ == "__main__":
    if len(sys.argv) == 2 and sys.argv[1] == "--html":
        print(page())
    else:
        main()
