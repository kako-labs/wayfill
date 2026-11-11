"""Install only canonical curated sprite output; encode and measure actual animated WebP."""
from pathlib import Path
import json, shutil, subprocess, hashlib, sys
from PIL import Image

repo=Path(__file__).resolve().parents[1]
run=Path(sys.argv[1]) if len(sys.argv)>1 else Path('D:/hackathon-tmp/fuel-pilot-animation-work/podong-v3')
out=repo/'web/assets/animation';out.mkdir(parents=True,exist_ok=True)
native=repo/'android/fuel-pilot-ui/src/main/res/drawable-nodpi'
manifest=json.loads((run/'manifest.json').read_text(encoding='utf-8'))
encoder=next(Path('D:/hackathon-tmp/fuel-pilot-animation-work/tools/libwebp').rglob('img2webp.exe'))
shutil.copy2(run/'sprite-sheet-alpha.png',out/'podong-atlas.png')
shutil.copy2(out/'podong-atlas.png',native/'podong_atlas.png')
data={'atlas':'assets/animation/podong-atlas.png','width':manifest['frame_layout']['sheetWidth'],'height':manifest['frame_layout']['sheetHeight'],'clips':{}}
for name,entry in manifest['animation']['rows'].items():
    sources=[run/'curated'/f'{name}-frame-{i}.png' for i in range(entry['frames'])]
    assert all(p.is_file() for p in sources),'curated output missing'
    target=out/f'{name}.webp'
    args=[str(encoder),'-loop','1','-lossless']
    for source in sources: args+=['-d',str(round(1000/entry['fps'])),str(source)]
    subprocess.run(args+['-o',str(target)],check=True)
    durations=[];im=Image.open(target)
    for i in range(im.n_frames):
        im.seek(i);im.load();durations.append(im.info.get('duration',round(1000/entry['fps'])))
    assert im.n_frames==entry['frames']
    # One fixed display viewport per entire clip; never independently resize poses.
    # Component extraction preserves native resolution, so longer rows have smaller ink.
    boxes=[Image.open(p).getchannel('A').point(lambda a:255 if a>20 else 0).getbbox() for p in sources]
    left=min(b[0] for b in boxes);top=min(b[1] for b in boxes);right=max(b[2] for b in boxes);bottom=max(b[3] for b in boxes)
    side=min(256,max(right-left,bottom-top)+16)
    viewport={'x':max(0,min(256-side,round((left+right-side)/2))),'y':max(0,min(256-side,round((top+bottom-side)/2))),'w':side,'h':side}
    data['clips'][name]={'file':f'assets/animation/{name}.webp','frames':manifest['frame_layout']['rows'][name],'viewport':viewport,'durationsMs':durations,'durationMs':sum(durations),'loop':False}
    shutil.copy2(run/'curated'/f'{name}-frame-{entry["frames"]-1}.png',out/f'{name}-rest.png')
    print(name,im.n_frames,sum(durations),target.stat().st_size)
(out/'manifest.json').write_text(json.dumps(data,ensure_ascii=False,indent=2),encoding='utf-8')
(repo/'web/animation-data.js').write_text('(function(r){const d='+json.dumps(data,separators=(',',':'))+';if(typeof module!=="undefined")module.exports=d;r.PodongData=d;})(typeof globalThis!=="undefined"?globalThis:this);\n',encoding='utf-8')
lines=['package dev.fuelpilot.ui','', '// Generated from the measured animated WebP manifest. Do not hand-copy coordinates.', 'internal data class SpriteRect(val x: Int,val y: Int,val w: Int,val h: Int)','internal data class SpriteClip(val frames: List<SpriteRect>,val durationsMs: List<Long>,val viewport: SpriteRect) { val durationMs get() = durationsMs.sum() }','internal object PodongClips {','    val clips = mapOf(']
for name,clip in data['clips'].items():
    rects=','.join('SpriteRect({x},{y},{w},{h})'.format(**v) for v in clip['frames'])
    durations=','.join(str(v)+'L' for v in clip['durationsMs'])
    viewport='SpriteRect({x},{y},{w},{h})'.format(**clip['viewport'])
    lines.append(f'        "{name}" to SpriteClip(listOf({rects}),listOf({durations}),{viewport}),')
lines+=['    )','}']
(repo/'android/fuel-pilot-ui/src/main/java/dev/fuelpilot/ui/PodongClips.kt').write_text('\n'.join(lines)+'\n',encoding='utf-8')
(out/'PROVENANCE.json').write_text(json.dumps({'provider':'codex','generation':{name:len(clip['frames']) for name,clip in data['clips'].items()},'pipeline':'prepare → gen-set → component extraction → compose-atlas → export-pngs → animated WebP','runFolder':str(run),'sourceAtlasSha256':hashlib.sha256((out/'podong-atlas.png').read_bytes()).hexdigest()},indent=2),encoding='utf-8')
